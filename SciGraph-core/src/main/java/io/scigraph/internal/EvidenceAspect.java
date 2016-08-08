/**
 * Copyright (C) 2014 The SciGraph authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.scigraph.internal;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newHashSet;
import io.scigraph.frames.NodeProperties;
import io.scigraph.neo4j.GraphUtil;

import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import com.google.common.base.Function;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;

/***
 * Add "evidence" to a graph
 */
public class EvidenceAspect implements GraphAspect {

  static final RelationshipType HAS_SUBJECT = RelationshipType.withName("http://purl.org/oban/association_has_subject");
  static final RelationshipType HAS_OBJECT = RelationshipType.withName("http://purl.org/oban/association_has_object");
  static final RelationshipType EVIDENCE = RelationshipType.withName("http://purl.obolibrary.org/obo/RO_0002558");
  static final RelationshipType SOURCE = RelationshipType.withName("http://purl.org/dc/elements/1.1/source");
  static final RelationshipType OBJECT_PROPERTY = RelationshipType.withName("http://purl.org/oban/association_has_object_property");

  private static final Logger logger = Logger.getLogger(EvidenceAspect.class.getName());

  private final GraphDatabaseService graphDb;

  @Inject
  EvidenceAspect(GraphDatabaseService graphDb) {
    this.graphDb = graphDb;
  }

  @Override
  public void invoke(Graph graph) {
    Set<Long> nodeIds = newHashSet(transform(graph.getVertices(), new Function<Vertex, Long>() {
      @Override
      public Long apply(Vertex vertex) {
        return Long.valueOf((String) vertex.getId());
      }
    }));
    try (Transaction tx = graphDb.beginTx()) {
      for (Vertex vertex : graph.getVertices()) {
        Node subject = graphDb.getNodeById(Long.parseLong((String) vertex.getId()));
        for (Relationship hasSubject : subject.getRelationships(HAS_SUBJECT, Direction.INCOMING)) {
          Node association = hasSubject.getOtherNode(subject);
          for (Relationship hasObject : association
              .getRelationships(HAS_OBJECT, Direction.OUTGOING)) {
            Node object = hasObject.getOtherNode(association);
            if (nodeIds.contains(object.getId())) {
              // check of the relationship is in the graph
              Iterator<Relationship> objectProperty =
                  association.getRelationships(OBJECT_PROPERTY, Direction.OUTGOING).iterator();
              if (objectProperty.hasNext()) {
                // an association has to have 1 and only 1 object property
                Node relationshipNode = objectProperty.next().getOtherNode(association);
                String objectPropertyRelationship =
                    GraphUtil.getProperty(relationshipNode, NodeProperties.IRI, String.class).get();

                boolean isEdgeInGraph = false;
                Iterator<Vertex> connectedVertices =
                    vertex.getVertices(com.tinkerpop.blueprints.Direction.BOTH,
                        objectPropertyRelationship).iterator();
                while (connectedVertices.hasNext()) {
                  if (Long.parseLong((String) connectedVertices.next().getId()) == object.getId()) {
                    isEdgeInGraph = true;
                  }
                }

                if (isEdgeInGraph) { // means that the relationship exists between the subject and
                                     // object
                  TinkerGraphUtil.addEdge(graph, hasSubject);
                  TinkerGraphUtil.addEdge(graph, hasObject);
                  for (Relationship evidence : association.getRelationships(EVIDENCE,
                      Direction.OUTGOING)) {
                    TinkerGraphUtil.addEdge(graph, evidence);
                  }
                  for (Relationship source : association.getRelationships(SOURCE,
                      Direction.OUTGOING)) {
                    TinkerGraphUtil.addEdge(graph, source);
                  }
                }
              } else {
                logger
                    .severe(GraphUtil.getProperty(association, NodeProperties.IRI, String.class)
                        .or(Long.toString(association.getId()))
                        + " does not have the relation 'http://purl.org/oban/association_has_object_property'. Ignoring this association.");
              }
            }
          }
        }
      }
      tx.success();
    }
  }

}
