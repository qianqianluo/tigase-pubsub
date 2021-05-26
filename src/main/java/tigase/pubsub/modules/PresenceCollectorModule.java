/*
 * Tigase PubSub - Publish Subscribe component for Tigase
 * Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.pubsub.modules;

import tigase.annotations.TigaseDeprecated;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubException;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.impl.PresenceCapabilitiesManager;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Bean(name = "presenceCollectorModule", parent = PubSubComponent.class, active = true)
public class PresenceCollectorModule
		extends AbstractPubSubModule {

	private static final ConcurrentMap<String, String[]> CAPS_MAP = new ConcurrentHashMap<String, String[]>();
	private static final Criteria CRIT = ElementCriteria.name("presence");
	private static final String[] EMPTY_CAPS = {};
	private final ConcurrentMap<BareJID, ConcurrentMap<BareJID, Map<String, String[]>>> presenceByService = new ConcurrentHashMap<>();
	@Inject
	private CapsModule capsModule;
	@Inject
	private EventBus eventBus;

	@TigaseDeprecated(since = "4.1.1", removeIn = "5.0.0", note = "Will be replaced by single CAPS node instead of array of nodes")
	@Deprecated
	public boolean addJid(final BareJID serviceJid, final JID jid, String[] caps) {
		if (jid == null) {
			return false;
		}

		// here we are using CAPS_MAP to cache instances of CAPS to reduce
		// memory footprint
		if (caps == null || caps.length == 0) {
			caps = EMPTY_CAPS;
		} else {
			StringBuilder sb = new StringBuilder();
			for (String item : caps) {
				sb.append(item);
			}
			String key = sb.toString();
			String[] cachedCaps = CAPS_MAP.putIfAbsent(key, caps);
			if (cachedCaps != null) {
				caps = cachedCaps;
			}
		}

		boolean added = false;
		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();

		ConcurrentMap<BareJID, Map<String, String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			ConcurrentMap<BareJID, Map<String, String[]>> tmp = new ConcurrentHashMap<>();
			presenceByUser = presenceByService.putIfAbsent(serviceJid, tmp);
			if (presenceByUser == null) {
				presenceByUser = tmp;
			}
		}

		if (resource != null) {
			Map<String, String[]> resources = presenceByUser.get(bareJid);

			if (resources == null) {
				Map<String, String[]> tmp = new HashMap<>();
				resources = presenceByUser.putIfAbsent(bareJid, tmp);
				if (resources == null) {
					resources = tmp;
				}
			}

			String[] oldCaps;
			String[] availableCaps = caps;
			synchronized (resources) {
				oldCaps = resources.put(resource, caps);
				added = oldCaps == null;
			}
			log.finest("for service " + serviceJid + " - Contact " + jid + " is collected.");

			// we are firing CapsChangeEvent only for PEP services
			if (this.config.isPepPeristent() && this.config.isSendLastPublishedItemOnPresence() &&
					(serviceJid.getLocalpart() != null || config.isSubscribeByPresenceFilteredNotifications()) && oldCaps != caps && caps != null) {
				// calculating new features and firing event
				Set<String> newFeatures = new HashSet<String>();
				for (String node : caps) {
					// ignore searching for features if same node exists in old
					// caps
					if (oldCaps != null && Arrays.binarySearch(oldCaps, node) >= 0) {
						continue;
					}

					String[] features = PresenceCapabilitiesManager.getNodeFeatures(node);
					if (features != null) {
						for (String feature : features) {
							newFeatures.add(feature);
						}
					}
				}
				if (oldCaps != null) {
					for (String node : oldCaps) {
						// ignore searching for features if same node exists in
						// new caps
						if (Arrays.binarySearch(caps, node) >= 0) {
							continue;
						}
						String[] features = PresenceCapabilitiesManager.getNodeFeatures(node);
						if (features != null) {
							for (String feature : features) {
								newFeatures.remove(feature);
							}
						}
					}
				}

				if (!newFeatures.isEmpty()) {
					fireCapsChangeEvent(serviceJid, jid, caps, oldCaps, newFeatures);
				}
			}
		}

		// onlineUsers.add(jid);
		return added;
	}

	public List<JID> getAllAvailableJids(final BareJID serviceJid) {
		ArrayList<JID> result = new ArrayList<JID>();

		ConcurrentMap<BareJID, Map<String, String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser != null) {
			for (Entry<BareJID, Map<String, String[]>> entry : presenceByUser.entrySet()) {
				for (String reource : entry.getValue().keySet()) {
					JID jid = JID.jidInstanceNS(entry.getKey(), reource);
					if (isAvailableLocally(jid)) {
						result.add(jid);
					}
				}
			}
		}

		return result;
	}

	public List<JID> getAllAvailableJidsWithFeature(final BareJID serviceJid, final String feature) {
		final List<JID> result = new ArrayList<>();
		ConcurrentMap<BareJID, Map<String, String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return result;
		}

		Set<String> nodesWithFeature = PresenceCapabilitiesManager.getNodesWithFeature(feature);
		for (Map.Entry<BareJID, Map<String, String[]>> pe : presenceByUser.entrySet()) {
			Map<String, String[]> jid_resources = pe.getValue();
			if (jid_resources != null) {
				synchronized (jid_resources) {
					for (Map.Entry<String, String[]> e : jid_resources.entrySet()) {
						String[] caps = e.getValue();
						boolean match = false;
						for (String node : caps) {
							match |= nodesWithFeature.contains(node);
						}
						if (match) {
							JID jid = JID.jidInstanceNS(pe.getKey(), e.getKey());
							if (isAvailableLocally(jid)) {
								result.add(jid);
							}
						}
					}
				}
			}
		}

		return result;
	}

	public List<JID> getAllAvailableResources(final BareJID serviceJid, final BareJID bareJid) {
		final List<JID> result = new ArrayList<JID>();
		ConcurrentMap<BareJID, Map<String, String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return result;
		}

		final Map<String, String[]> jid_resources = presenceByUser.get(bareJid);

		if (jid_resources != null) {
			for (String reource : jid_resources.keySet()) {
				JID jid = JID.jidInstanceNS(bareJid, reource);
				if (isAvailableLocally(jid)) {
					result.add(jid);
				}
			}
		}

		return result;
	}

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#presence-notifications"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	public boolean isJidAvailable(final BareJID serviceJid, final BareJID bareJid) {
		ConcurrentMap<BareJID, Map<String, String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return false;
		}
		final Map<String, String[]> resources = presenceByUser.get(bareJid);

		return (resources != null) && (resources.size() > 0);
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		final StanzaType type = packet.getType();
		final JID jid = packet.getStanzaFrom();
		final JID toJid = packet.getStanzaTo();
		if (jid == null || toJid == null) {
			return;
		}
		// why it is here if it is also below?
		// PresenceChangeEvent event = new PresenceChangeEvent( packet );
		// config.getEventBus().fire( event, this );

		if (type == null || type == StanzaType.available) {
			String[] caps = config.isPepPeristent() ? capsModule.processPresence(packet) : null;
			boolean added = addJid(toJid.getBareJID(), jid, caps);
			firePresenceChangeEvent(packet);
			if (added && packet.getStanzaTo().getLocalpart() == null) {
				Packet p = new Presence(new Element("presence", new String[]{"to", "from", Packet.XMLNS_ATT},
													new String[]{jid.toString(), toJid.toString(),
																 Packet.CLIENT_XMLNS}), toJid, jid);

				packetWriter.write(p);
			}
		} else if (StanzaType.unavailable == type) {
			removeJid(toJid.getBareJID(), jid);
			firePresenceChangeEvent(packet);
			if (packet.getStanzaTo().getLocalpart() == null) {
				Packet p = new Presence(new Element("presence", new String[]{"to", "from", "type", Packet.XMLNS_ATT},
													new String[]{jid.toString(), toJid.toString(),
																 StanzaType.unavailable.toString(),
																 Packet.CLIENT_XMLNS}), toJid, jid);

				packetWriter.write(p);
			}
		} else if (StanzaType.subscribe == type) {
			log.finest("Contact " + jid + " wants to subscribe PubSub");

			Packet presence = preparePresence(packet, StanzaType.subscribed);

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, StanzaType.subscribe);
			if (presence != null) {
				packetWriter.write(presence);
			}
		} else if (StanzaType.unsubscribe == type || StanzaType.unsubscribed == type) {
			log.finest("Contact " + jid + " wants to unsubscribe PubSub");

			Packet presence = preparePresence(packet, StanzaType.unsubscribed);

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, StanzaType.unsubscribe);
			if (presence != null) {
				packetWriter.write(presence);
			}
		}

	}

	protected boolean isAvailableLocally(JID jid) {
		return true;
	}

	protected boolean removeJid(final BareJID serviceJid, final JID jid) {
		if (jid == null) {
			return false;
		}

		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();
		boolean removed = false;

		ConcurrentMap<BareJID, Map<String, String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return false;
		}

		// onlineUsers.remove(jid);
		if (resource == null) {
			presenceByUser.remove(bareJid);
			fireBuddyVisibilityEvent(bareJid, false);
		} else {
			Map<String, String[]> resources = presenceByUser.get(bareJid);

			if (resources != null) {
				synchronized (resources) {
					removed = resources.remove(resource) != null;
					log.finest("for service " + serviceJid + " - Contact " + jid + " is removed from collection.");
					if (resources.isEmpty()) {
						presenceByUser.remove(bareJid);
						fireBuddyVisibilityEvent(bareJid, false);
					}
				}
			}
		}

		return removed;
	}

	private void fireBuddyVisibilityEvent(BareJID bareJid, boolean b) {
		eventBus.fire(new BuddyVisibilityEvent(bareJid, b));
	}

	private void fireCapsChangeEvent(BareJID serviceJid, JID jid, String[] caps, String[] oldCaps,
									 Set<String> newFeatures) {
		eventBus.fire(new CapsChangeEvent(serviceJid, jid, caps, oldCaps, newFeatures));
	}

	private void firePresenceChangeEvent(Packet packet) {
		eventBus.fire(new PresenceChangeEvent(packet));
	}

	private Packet preparePresence(final Packet presence, StanzaType type) {
		JID to = presence.getTo();
		JID from = presence.getStanzaFrom();

		if (from != null && to != null && !((from.getBareJID()).equals(to.getBareJID()))) {
			JID jid = from.copyWithoutResource();
			Element p = new Element("presence", new String[]{"to", "from", Packet.XMLNS_ATT},
									new String[]{jid.toString(), to.toString(), Packet.CLIENT_XMLNS});

			if (type != null) {
				p.setAttribute("type", type.toString());
			}

			return new Presence(p, to, from);
		}

		return null;
	}

	public static class BuddyVisibilityEvent {

		public final boolean becomeOnline;
		public final BareJID buddyJID;

		public BuddyVisibilityEvent(BareJID buddyJID, boolean becomeOnline) {
			this.buddyJID = buddyJID;
			this.becomeOnline = becomeOnline;
		}

	}

	public static class CapsChangeEvent {

		public final JID buddyJid;
		public final String[] newCaps;
		public final Set<String> newFeatures;
		public final String[] oldCaps;
		public final BareJID serviceJid;

		@TigaseDeprecated(since = "4.1.1", removeIn = "5.0.0", note = "Will be replaced by single CAPS node instead of array of nodes")
		@Deprecated
		public CapsChangeEvent(BareJID serviceJid, JID buddyJid, String[] newCaps, String[] oldCaps,
							   Set<String> newFeatures) {
			this.serviceJid = serviceJid;
			this.buddyJid = buddyJid;
			this.newCaps = newCaps;
			this.oldCaps = oldCaps;
			this.newFeatures = newFeatures;
		}

	}

	public static class PresenceChangeEvent {

		public final Packet packet;

		public PresenceChangeEvent(Packet packet) {
			this.packet = packet;
		}

	}

}
