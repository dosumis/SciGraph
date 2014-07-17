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
package edu.sdsc.scigraph.internal.reachability;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import com.google.common.base.Predicate;

public class ReachabilityIndexTest {

  static final RelationshipType type = DynamicRelationshipType.withName("foo");

  static ReachabilityIndex index;
  static Node a, b, c, d, e, f;

  @BeforeClass
  public static void setup() throws InterruptedException {
    GraphDatabaseService graphDb = new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder()
        .newGraphDatabase();
    try (Transaction tx = graphDb.beginTx()) {
      a = graphDb.createNode();
      b = graphDb.createNode();
      a.createRelationshipTo(b, type);
      c = graphDb.createNode();
      a.createRelationshipTo(c, type);
      c.createRelationshipTo(a, type);

      d = graphDb.createNode();

      e = graphDb.createNode();
      f = graphDb.createNode();
      e.createRelationshipTo(f, type);
      a.createRelationshipTo(e, type);

      tx.success();
    }
    index = new ReachabilityIndex(graphDb);
    index.createIndex(new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        return !input.equals(e);
      }
    });
  }

  @Test
  public void testEmptyGraph() {
    GraphDatabaseService graphDb = new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder()
        .newGraphDatabase();
    new ReachabilityIndex(graphDb);
  }

  @Test(expected = IllegalStateException.class)
  public void testUncreatedIndex() {
    GraphDatabaseService graphDb = new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder()
        .newGraphDatabase();
    ReachabilityIndex index = new ReachabilityIndex(graphDb);
    index.canReach(a, b);
  }

  @Test
  public void testSelfReachability() {
    assertThat(index.canReach(a, a), is(true));
  }

  @Test
  public void testDirectionalReachability() {
    assertThat(index.canReach(a, b), is(true));
    assertThat(index.canReach(b, a), is(false));
  }

  @Test
  public void testBidirectionalReachability() {
    assertThat(index.canReach(a, c), is(true));
    assertThat(index.canReach(b, c), is(false));
  }

  @Test
  public void testDisconnectedNode() {
    for (Node n : newArrayList(a, b, c)) {
      assertThat(index.canReach(n, d), is(false));
      assertThat(index.canReach(d, n), is(false));
    }
  }

  @Test
  public void testForbiddenNodes() {
    assertThat(index.canReach(a, f), is(false));
    assertThat(index.canReach(e, f), is(false));
    assertThat(index.canReach(a, e), is(false));
  }

}
