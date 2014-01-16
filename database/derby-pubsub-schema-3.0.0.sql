-- -----------------------------------------------------------------------------
-- Tables
-- -----------------------------------------------------------------------------

-- Table to store service jids
-- QUERY START:
create table tig_pubsub_service_jids (
	service_id bigint generated by default as identity not null,
	service_jid varchar(2049) not null,
	
	primary key ( service_id )
);
-- QUERY END:

-- QUERY START:
create unique index tig_pubsub_service_jids_service_jid on tig_pubsub_service_jids ( service_jid );
-- QUERY END:

-- Table to store jids of node owners, subscribers and affiliates
-- QUERY START:
create table tig_pubsub_jids (
	jid_id bigint generated by default as identity not null,
	jid varchar(2049) not null,

	primary key ( jid_id )
);
-- QUERY END:

-- QUERY START:
create unique index tig_pubsub_jids_jid on tig_pubsub_jids ( jid );
-- QUERY END:

-- Table to store nodes configuration
-- QUERY START:
create table tig_pubsub_nodes (
	node_id bigint generated by default as identity not null,
	service_id bigint not null references tig_pubsub_service_jids ( service_id ),
	name varchar(1024) not null,
	type int not null,
	title varchar(1000),
	description varchar(32672),
	creator_id bigint references tig_pubsub_jids ( jid_id ),
	creation_date timestamp,
	configuration varchar(32672),
	collection_id bigint references tig_pubsub_nodes ( node_id ),
	
	primary key ( node_id )
);
-- QUERY END:

-- QUERY START:
create index tig_pubsub_nodes_service_id on tig_pubsub_nodes ( service_id );
-- QUERY END:
-- QUERY START:
create index tig_pubsub_nodes_name on tig_pubsub_nodes ( name );
-- QUERY END:
-- QUERY START:
create unique index tig_pubsub_nodes_service_id_name on tig_pubsub_nodes ( service_id, name );
-- QUERY END:
-- QUERY START:
create index tig_pubsub_nodes_collection_id on tig_pubsub_nodes ( collection_id );
-- QUERY END:

-- Table to store user nodes affiliations
-- QUERY START:
create table tig_pubsub_affiliations (
	node_id bigint not null references tig_pubsub_nodes ( node_id ),
	jid_id bigint not null references tig_pubsub_jids ( jid_id ),
	affiliation varchar(20) not null,

	primary key ( node_id, jid_id )
);
-- QUERY END:

-- QUERY START:
create index tig_pubsub_affiliations_node_id on tig_pubsub_affiliations ( node_id );
-- QUERY END:
-- QUERY START:
create index tig_pubsub_affiliations_jid_id on tig_pubsub_affiliations ( jid_id );
-- QUERY END:

-- Table to store user nodes subscriptions
-- QUERY START:
create table tig_pubsub_subscriptions (
	node_id bigint not null references tig_pubsub_nodes ( node_id ),
	jid_id bigint not null references tig_pubsub_jids ( jid_id ),
	subscription varchar(20) not null,
	subscription_id varchar(40) not null,

	primary key ( node_id, jid_id )
);
-- QUERY END:

-- QUERY START:
create index tig_pubsub_subscriptions_node_id on tig_pubsub_subscriptions ( node_id );
-- QUERY END:
-- QUERY START:
create index tig_pubsub_subscriptions_jid_id on tig_pubsub_jids ( jid_id );
-- QUERY END:

-- Table to store items
-- QUERY START:
create table tig_pubsub_items (
	node_id bigint not null references tig_pubsub_nodes ( node_id ),
	id varchar(1024) not null,
	creation_date timestamp,
	publisher_id bigint references tig_pubsub_jids ( jid_id ),
	update_date timestamp,
	data varchar(32672),

	primary key ( node_id, id )
);
-- QUERY END:

-- QUERY START:
create index tig_pubsub_items_node_id on tig_pubsub_items ( node_id );
-- QUERY END:
-- QUERY START:
create index tig_pubsub_items_id on tig_pubsub_items ( id );
-- QUERY END:

-- -----------------------------------------------------------------------------
-- Functions
-- -----------------------------------------------------------------------------
-- QUERY START:
create procedure TigPubSubCreateNode(service_jid varchar(2049), node_name varchar(1024),
	node_type int, node_creator varchar(2049), node_conf varchar(32672), collection_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubCreateNode';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubRemoveNode(node_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubRemoveNode';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetItem(node_id bigint, item_id varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetItem';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubWriteItem(node_id bigint, item_id varchar(1024),
	publisher varchar(2049), item_data varchar(32672))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubWriteItem';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteItem(node_id bigint, item_id varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubDeleteItem';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeId(service_jid varchar(2049), node_name varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeId';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsIds(node_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeItemIds';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsIdsSince(node_id bigint, since timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeItemIdsSince';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetAllNodes(service_jid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetAllNodes';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetRootNodes(service_jid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetRootNodes';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetChildNodes(service_jid varchar(2049), node_name varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetChildNodes';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteAllNodes(service_jid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubDeleteAllNodes';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeConfiguration(node_id bigint, node_conf varchar(32672), collection_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubSetNodeConfiguration';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeAffiliation(node_id bigint, jid varchar(2049), affil varchar(20))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubSetNodeAffiliation';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeConfiguration(node_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeConfiguration';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeAffiliations(node_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeAffiliations';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeSubscriptions(node_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeSubscriptions';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeSubscription(node_id bigint, jid varchar(2049), subscr varchar(20),
	subscr_id varchar(40))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubSetNodeSubscription';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteNodeSubscription(node_id bigint, jid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubDeleteNodeSubscription';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetUserAffiliations(service_jid varchar(2049), jid varchar(2049)) 
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetUserAffiliations';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetUserSubscriptions(service_jid varchar(2049), jid varchar(2049)) 
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetUserSubscriptions';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsMeta(node_id bigint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeItemsMeta';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubFixNode(node_id bigint, creation_date timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubFixNode';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubFixItem(node_id bigint, item_id varchar(1024),
	creation_date timestamp, update_date timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubFixItem';
-- QUERY END:
