package edu.sdsc.scigraph.neo4j;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import com.google.common.base.Optional;

public class GraphInterfaceTransactionImpl implements GraphInterface {

  private static Object graphLock = new Object();
  private final ConcurrentMap<String, Long> idMap;
  private final RelationshipMap relationshipMap;
  private final GraphDatabaseService graphDb;

  @Inject
  public GraphInterfaceTransactionImpl(GraphDatabaseService graphDb,
      ConcurrentMap<String, Long> idMap, RelationshipMap relationshipMap) {
    this.idMap = idMap;
    this.relationshipMap = relationshipMap;
    this.graphDb = graphDb;
  }

  @Override
  public void shutdown() {
    graphDb.shutdown();
  }

  @Override
  public long createNode(String id) {
    if (idMap.containsKey(id)) {
      return idMap.get(id);
    } else {
      synchronized (graphLock) {
        Node node;
        try (Transaction tx = graphDb.beginTx()) {
          node = graphDb.createNode();
          tx.success();
        }
        idMap.put(id, node.getId());
        return node.getId();
      }
    }
  }

  @Override
  public Optional<Long> getNode(String id) {
    if (idMap.containsKey(id)) {
      return Optional.of(idMap.get(id));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public long createRelationship(long start, long end, RelationshipType type) {
    Optional<Long> relationshipId = getRelationship(start, end, type);

    if (relationshipId.isPresent()) {
      return relationshipId.get();
    } else {
      try (Transaction tx = graphDb.beginTx()) {
        Node startNode = graphDb.getNodeById(start);
        Node endNode = graphDb.getNodeById(end);
        Relationship relationship;
        synchronized (graphLock) {
          relationship = startNode.createRelationshipTo(endNode, type);
          relationshipMap.put(start, end, type, relationship.getId());
        }
        tx.success();
        return relationship.getId();
      }
    }
  }

  @Override
  public Collection<Long> createRelationshipsPairwise(Collection<Long> nodeIds,
      RelationshipType type) {
    Set<Long> relationships = new HashSet<>();
    for (Long start : nodeIds) {
      for (Long end : nodeIds) {
        if (start.equals(end)) {
          continue;
        } else {
          createRelationship(start, end, type);
        }
      }
    }
    return relationships;
  }

  @Override
  public Optional<Long> getRelationship(long start, long end, RelationshipType type) {
    if (relationshipMap.containsKey(start, end, type)) {
      return Optional.of(relationshipMap.get(start, end, type));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public void setNodeProperty(long nodeId, String property, Object value) {
    try (Transaction tx = graphDb.beginTx()) {
      Node node = graphDb.getNodeById(nodeId);
      node.setProperty(property, value);
      tx.success();
    }
  }

  @Override
  public void addNodeProperty(long nodeId, String property, Object value) {
    try (Transaction tx = graphDb.beginTx()) {
      Node node = graphDb.getNodeById(nodeId);
      if (node.hasProperty(property)) {
        node.setProperty(property, GraphUtil.getNewPropertyValue(node.getProperty(property), value));
      } else {
        node.setProperty(property, value);
      }
      tx.success();
    }
  }

  @Override
  public <T> Optional<T> getNodeProperty(long nodeId, String property, Class<T> type) {
    try (Transaction tx = graphDb.beginTx()) {
      Node node = graphDb.getNodeById(nodeId);
      Optional<T> value = Optional.<T> absent();
      if (node.hasProperty(property)) {
        value = Optional.<T> of(type.cast(node.getProperty(property)));
      }
      tx.success();
      return value;
    }
  }

  @Override
  public <T> List<T> getNodeProperties(long nodeId, String property, Class<T> type) {
    try (Transaction tx = graphDb.beginTx()) {
      Node node = graphDb.getNodeById(nodeId);
      List<T> list = emptyList();
      if (node.hasProperty(property)) {
        list = GraphUtil.getPropertiesAsList(node.getProperty(property), type);
      }
      tx.success();
      return list;
    }
  }

  @Override
  public void setRelationshipProperty(long relationshipId, String property, Object value) {
    try (Transaction tx = graphDb.beginTx()) {
      Relationship relationship = graphDb.getRelationshipById(relationshipId);
      relationship.setProperty(property, value);
      tx.success();
    }
  }

  @Override
  public void addRelationshipProperty(long relationshipId, String property, Object value) {
    try (Transaction tx = graphDb.beginTx()) {
      Relationship relationship = graphDb.getRelationshipById(relationshipId);
      if (relationship.hasProperty(property)) {
        relationship.setProperty(property, GraphUtil.getNewPropertyValue(relationship.getProperty(property), value));
      } else {
        relationship.setProperty(property, value);
      }
      tx.success();
    }
  }

  @Override
  public <T> Optional<T> getRelationshipProperty(long relationshipId, String property,
      Class<T> type) {
    try (Transaction tx = graphDb.beginTx()) {
      Relationship relationship = graphDb.getRelationshipById(relationshipId);
      Optional<T> value = Optional.<T> absent();
      if (relationship.hasProperty(property)) {
        value = Optional.<T> of(type.cast(relationship.getProperty(property)));
      }
      tx.success();
      return value;
    }
  }

  @Override
  public <T> List<T> getRelationshipProperties(long relationshipId, String property,
      Class<T> type) {
    try (Transaction tx = graphDb.beginTx()) {
      Relationship relationship = graphDb.getRelationshipById(relationshipId);
      List<T> list = emptyList();
      if (relationship.hasProperty(property)) {
        list = GraphUtil.getPropertiesAsList(relationship.getProperty(property), type);
      }
      tx.success();
      return list;
    }
  }

  @Override
  public void setLabel(long nodeId, Label label) {
    try (Transaction tx = graphDb.beginTx()) {
      Node node = graphDb.getNodeById(nodeId);
      for (Label currentLabel: node.getLabels()) {
        node.removeLabel(currentLabel);
      }
      node.addLabel(label);
      tx.success();
    }
  }

  @Override
  public void addLabel(long nodeId, Label label) {
    try (Transaction tx = graphDb.beginTx()) {
      Node node = graphDb.getNodeById(nodeId);
      node.addLabel(label);
      tx.success();
    }
  }

  @Override
  public Collection<Label> getLabels(long nodeId) {
    try (Transaction tx = graphDb.beginTx()) {
      Node node = graphDb.getNodeById(nodeId);
      Collection<Label> labels = newArrayList(node.getLabels());
      tx.success();
      return labels;
    }
  }

}
