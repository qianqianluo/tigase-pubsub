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
/*
 Retrieve PubSub node item

 AS:Description: Retrieve item
 AS:CommandId: retrieve-item
 AS:Component: pubsub
 */

package tigase.admin

import groovy.transform.CompileStatic
import tigase.eventbus.EventBus
import tigase.kernel.core.Kernel
import tigase.pubsub.PubSubComponent
import tigase.pubsub.PubSubConfig
import tigase.pubsub.exceptions.PubSubException
import tigase.pubsub.repository.IItems
import tigase.pubsub.repository.IPubSubRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.xml.Element
import tigase.xmpp.Authorization

Kernel kernel = (Kernel) kernel;
PubSubComponent component = (PubSubComponent) component
packet = (Iq) packet
eventBus = (EventBus) eventBus

@CompileStatic
Packet process(Kernel kernel, PubSubComponent component, Iq p, EventBus eventBus, Set admins) {

	def componentConfig = kernel.getInstance(PubSubConfig.class)

	IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);

	def NODE = "node"
	def ITEM_ID = "item-id";

	def stanzaFromBare = p.getStanzaFrom().getBareJID()
	def isServiceAdmin = admins.contains(stanzaFromBare)

	Packet result = null;
	def node = Command.getFieldValue(p, NODE);
	def itemId = Command.getFieldValue(p, ITEM_ID);

	try {
		if (!node || !itemId) {
			result = p.commandResult(Command.DataType.form);
			Command.addTitle(result, "Retrive PubSub node item");
			Command.addInstructions(result, "Fill out this form to retrieve published item");
			Command.addFieldValue(result, NODE, node ?: "", "text-single", "Node");
			Command.addFieldValue(result, ITEM_ID, itemId ?: "", "text-single", "Item ID");
		} else {
			result = p.commandResult(Command.DataType.result);
			Command.addTitle(result, "Retrive PubSub node item");
			if (isServiceAdmin || componentConfig.isAdmin(stanzaFromBare)) {
				Command.addFieldValue(result, NODE, node ?: "", "text-single", "Node");
				Command.addFieldValue(result, ITEM_ID, itemId ?: "", "text-single", "Item ID");
				def nodeConfig = pubsubRepository.getNodeConfig(p.getStanzaTo().getBareJID(), node);
				if (nodeConfig == null) {
					throw new PubSubException(Authorization.ITEM_NOT_FOUND,
											  "Node " + node + " was not found")
				};
				IItems items = pubsubRepository.getNodeItems(p.getStanzaTo().getBareJID(), node);
				IItems.IItem item = items.getItem(itemId);
				if (item == null) {
					throw new PubSubException(Authorization.ITEM_NOT_FOUND,
											  "Item " + itemId + " was not found")
				};
				Command.addFieldValue(result, "item", item.getItem().toString(), "text-multi", "Item");
			} else {
				throw new PubSubException(Authorization.FORBIDDEN,
										  "You do not have enough " + "permissions to retrieve item from a node.");
			}
		}
	} catch (PubSubException ex) {
		Command.addTextField(result, "Error", ex.getMessage())
		if (ex.getErrorCondition()) {
			def error = ex.getErrorCondition();
			Element errorEl = new Element("error");
			errorEl.setAttribute("type", error.getErrorType());
			Element conditionEl = new Element(error.getCondition(), ex.getMessage());
			conditionEl.setXMLNS(Packet.ERROR_NS);
			errorEl.addChild(conditionEl);
			Element pubsubCondition = ex.pubSubErrorCondition?.getElement();
			if (pubsubCondition) {
				errorEl.addChild(pubsubCondition)
			};
			result.getElement().addChild(errorEl);
		}
	}

	return result;
}

return process(kernel, component, packet, eventBus, (Set) adminsSet);
