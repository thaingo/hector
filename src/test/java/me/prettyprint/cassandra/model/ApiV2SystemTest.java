package me.prettyprint.cassandra.model;

import static me.prettyprint.cassandra.model.HFactory.createColumn;
import static me.prettyprint.cassandra.model.HFactory.createColumnQuery;
import static me.prettyprint.cassandra.model.HFactory.createKeyspaceOperator;
import static me.prettyprint.cassandra.model.HFactory.createMultigetSliceQuery;
import static me.prettyprint.cassandra.model.HFactory.createMultigetSubSliceQuery;
import static me.prettyprint.cassandra.model.HFactory.createMultigetSuperSliceQuery;
import static me.prettyprint.cassandra.model.HFactory.createMutator;
import static me.prettyprint.cassandra.model.HFactory.createSliceQuery;
import static me.prettyprint.cassandra.model.HFactory.createSubSliceQuery;
import static me.prettyprint.cassandra.model.HFactory.createSuperColumn;
import static me.prettyprint.cassandra.model.HFactory.createSuperColumnQuery;
import static me.prettyprint.cassandra.model.HFactory.createSuperSliceQuery;
import static me.prettyprint.cassandra.model.HFactory.getOrCreateCluster;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import me.prettyprint.cassandra.BaseEmbededServerSetupTest;
import me.prettyprint.cassandra.extractors.StringExtractor;
import me.prettyprint.cassandra.service.Cluster;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiV2SystemTest extends BaseEmbededServerSetupTest {

  private static final Logger log = LoggerFactory.getLogger(ApiV2SystemTest.class);
  private final static String KEYSPACE = "Keyspace1";
  private static final StringExtractor se = new StringExtractor();
  private Cluster cluster;
  private KeyspaceOperator ko;

  @Before
  public void setupCase() {
    cluster = getOrCreateCluster("MyCluster", "127.0.0.1:9170");
    ko = createKeyspaceOperator(KEYSPACE, cluster);
  }

  @After
  public void teardownCase() {
    ko = null;
    cluster = null;
  }

  @Test
  public void testInsertGetRemove() {
    String cf = "Standard1";

    Mutator m = createMutator(ko);
    MutationResult mr = m.insert("testInsertGetRemove", cf,
        createColumn("testInsertGetRemove", "testInsertGetRemove_value_", se, se));

    // Check the mutation result metadata
    // assertEquals("127.0.0.1:9170", mr.getHostUsed());
    assertTrue("Time should be > 0", mr.getExecutionTimeMicro() > 0);
    log.debug("insert execution time: {}", mr.getExecutionTimeMicro());

    // get value
    ColumnQuery<String, String> q = createColumnQuery(ko, se, se);
    q.setName("testInsertGetRemove").setColumnFamily(cf);
    Result<HColumn<String, String>> r = q.setKey("testInsertGetRemove").execute();
    assertNotNull(r);
    HColumn<String, String> c = r.get();
    assertNotNull(c);
    String value = c.getValue();
    assertEquals("testInsertGetRemove_value_", value);
    String name = c.getName();
    assertEquals("testInsertGetRemove", name);
    assertEquals(q, r.getQuery());
    assertTrue("Time should be > 0", r.getExecutionTimeMicro() > 0);

    // remove value
    m = createMutator(ko);
    MutationResult mr2 = m.delete("testInsertGetRemove", cf, "testInsertGetRemove", se);
    assertTrue("Time should be > 0", mr2.getExecutionTimeMicro() > 0);

    // get already removed value
    ColumnQuery<String, String> q2 = createColumnQuery(ko, se, se);
    q2.setName("testInsertGetRemove").setColumnFamily(cf);
    Result<HColumn<String, String>> r2 = q2.setKey("testInsertGetRemove").execute();
    assertNotNull(r2);
    assertNull("Value should have been deleted", r2.get());
  }

  @Test
  public void testBatchInsertGetRemove() {
    String cf = "Standard1";

    Mutator m = createMutator(ko);
    for (int i = 0; i < 5; i++) {
      m.addInsertion("testInsertGetRemove" + i, cf,
          createColumn("testInsertGetRemove", "testInsertGetRemove_value_" + i, se, se));
    }
    m.execute();

    // get value
    ColumnQuery<String, String> q = createColumnQuery(ko, se, se);
    q.setName("testInsertGetRemove").setColumnFamily(cf);
    for (int i = 0; i < 5; i++) {
      Result<HColumn<String, String>> r = q.setKey("testInsertGetRemove" + i).execute();
      assertNotNull(r);
      HColumn<String, String> c = r.get();
      assertNotNull(c);
      String value = c.getValue();
      assertEquals("testInsertGetRemove_value_" + i, value);
    }

    // remove value
    m = createMutator(ko);
    for (int i = 0; i < 5; i++) {
      m.addDeletion("testInsertGetRemove" + i, cf, "testInsertGetRemove", se);
    }
    m.execute();

    // get already removed value
    ColumnQuery<String, String> q2 = createColumnQuery(ko, se, se);
    q2.setName("testInsertGetRemove").setColumnFamily(cf);
    for (int i = 0; i < 5; i++) {
      Result<HColumn<String, String>> r = q2.setKey("testInsertGetRemove" + i).execute();
      assertNotNull(r);
      assertNull("Value should have been deleted", r.get());
    }
  }

  @Test
  public void testSuperInsertGetRemove() {
    String cf = "Super1";

    Mutator m = createMutator(ko);

    @SuppressWarnings("unchecked")
    // aye, varargs and generics aren't good friends...
    List<HColumn<String, String>> columns = Arrays.asList(createColumn("name1", "value1", se, se),
        createColumn("name2", "value2", se, se));
    m.insert("testSuperInsertGetRemove", cf,
        createSuperColumn("testSuperInsertGetRemove", columns, se, se, se));

    // get value
    SuperColumnQuery<String, String, String> q = createSuperColumnQuery(ko, se, se, se);
    q.setSuperName("testSuperInsertGetRemove").setColumnFamily(cf);
    Result<HSuperColumn<String, String, String>> r = q.setKey("testSuperInsertGetRemove").execute();
    assertNotNull(r);
    HSuperColumn<String, String, String> sc = r.get();
    assertNotNull(sc);
    assertEquals(2, sc.getSize());
    HColumn<String, String> c = sc.get(0);
    String value = c.getValue();
    assertEquals("value1", value);
    String name = c.getName();
    assertEquals("name1", name);

    HColumn<String, String> c2 = sc.get(1);
    assertEquals("name2", c2.getName());
    assertEquals("value2", c2.getValue());

    // remove value
    m = createMutator(ko);
    m.superDelete("testSuperInsertGetRemove", cf, "testSuperInsertGetRemove", null, se, se);

    // test after removal
    r = q.execute();
    sc = r.get();
    assertNull(sc);
  }

  @Test
  public void testMultigetSliceQuery() {
    String cf = "Standard1";

    TestCleanupDescriptor cleanup = insertColumns(cf, 4, "testMultigetSliceQuery", 3,
        "testMultigetSliceQueryColumn");

    // get value
    MultigetSliceQuery<String, String> q = createMultigetSliceQuery(ko, se, se);
    q.setColumnFamily(cf);
    q.setKeys("testMultigetSliceQuery1", "testMultigetSliceQuery2");
    // try with column name first
    q.setColumnNames("testMultigetSliceQueryColumn1", "testMultigetSliceQueryColumn2");
    Result<Rows<String, String>> r = q.execute();
    assertNotNull(r);
    Rows<String, String> rows = r.get();
    assertNotNull(rows);
    assertEquals(2, rows.getCount());
    Row<String, String> row = rows.getByKey("testMultigetSliceQuery1");
    assertNotNull(row);
    assertEquals("testMultigetSliceQuery1", row.getKey());
    ColumnSlice<String, String> slice = row.getColumnSlice();
    assertNotNull(slice);
    // Test slice.getColumnByName
    assertEquals("value11", slice.getColumnByName("testMultigetSliceQueryColumn1").getValue());
    assertEquals("value12", slice.getColumnByName("testMultigetSliceQueryColumn2").getValue());
    assertNull(slice.getColumnByName("testMultigetSliceQueryColumn3"));
    // Test slice.getColumns
    List<HColumn<String, String>> columns = slice.getColumns();
    assertNotNull(columns);
    assertEquals(2, columns.size());

    // now try with start/finish
    q = createMultigetSliceQuery(ko, se, se);
    q.setColumnFamily(cf);
    q.setKeys("testMultigetSliceQuery3");
    q.setRange("testMultigetSliceQueryColumn1", "testMultigetSliceQueryColumn3", false, 100);
    r = q.execute();
    assertNotNull(r);
    rows = r.get();
    assertEquals(1, rows.getCount());
    for (Row<String, String> row2 : rows) {
      assertNotNull(row2);
      slice = row2.getColumnSlice();
      assertNotNull(slice);
      for (HColumn<String, String> column : slice.getColumns()) {
        if (!column.getName().equals("testMultigetSliceQueryColumn1")
            && !column.getName().equals("testMultigetSliceQueryColumn2")
            && !column.getName().equals("testMultigetSliceQueryColumn3")) {
          fail("A columns with unexpected column name returned: " + column.getName());
        }
      }
    }

    // Delete values
    deleteColumns(cleanup);
  }

  @Test
  public void testSlicesQuery() {
    String cf = "Standard1";

    Mutator m = createMutator(ko);
    for (int j = 1; j <= 3; ++j) {
      m.addInsertion("testSlicesQuery", cf,
          createColumn("testSlicesQuery" + j, "value" + j, se, se));
    }
    MutationResult mr = m.execute();
    assertTrue("Time should be > 0", mr.getExecutionTimeMicro() > 0);
    log.debug("insert execution time: {}", mr.getExecutionTimeMicro());

    // get value
    SliceQuery<String, String> q = createSliceQuery(ko, se, se);
    q.setColumnFamily(cf);
    q.setKey("testSlicesQuery");
    // try with column name first
    q.setColumnNames("testSlicesQuery1", "testSlicesQuery2", "testSlicesQuery3");
    Result<ColumnSlice<String, String>> r = q.execute();
    assertNotNull(r);
    ColumnSlice<String, String> slice = r.get();
    assertNotNull(slice);
    assertEquals(3, slice.getColumns().size());
    // Test slice.getColumnByName
    assertEquals("value1", slice.getColumnByName("testSlicesQuery1").getValue());
    assertEquals("value2", slice.getColumnByName("testSlicesQuery2").getValue());
    assertEquals("value3", slice.getColumnByName("testSlicesQuery3").getValue());
    // Test slice.getColumns
    List<HColumn<String, String>> columns = slice.getColumns();
    assertNotNull(columns);
    assertEquals(3, columns.size());

    // now try with start/finish
    q = createSliceQuery(ko, se, se);
    q.setColumnFamily(cf);
    q.setKey("testSlicesQuery");
    // try reversed this time
    q.setRange("testSlicesQuery2", "testSlicesQuery1", true, 100);
    r = q.execute();
    assertNotNull(r);
    slice = r.get();
    assertNotNull(slice);
    for (HColumn<String, String> column : slice.getColumns()) {
      if (!column.getName().equals("testSlicesQuery1")
          && !column.getName().equals("testSlicesQuery2")) {
        fail("A columns with unexpected column name returned: " + column.getName());
      }
    }

    // Delete values
    for (int j = 1; j <= 3; ++j) {
      m.addDeletion("testSlicesQuery", cf, "testSlicesQuery" + j, se);
    }
    mr = m.execute();
  }

  @Test
  public void testSuperSliceQuery() {
    String cf = "Super1";

    Mutator m = createMutator(ko);
    for (int j = 1; j <= 3; ++j) {
      @SuppressWarnings("unchecked")
      HSuperColumn<String, String, String> sc = createSuperColumn("testSuperSliceQuery" + j,
          Arrays.asList(createColumn("name", "value", se, se)), se, se, se);
      m.addInsertion("testSuperSliceQuery", cf, sc);
    }

    MutationResult mr = m.execute();
    assertTrue("Time should be > 0", mr.getExecutionTimeMicro() > 0);
    log.debug("insert execution time: {}", mr.getExecutionTimeMicro());

    // get value
    SuperSliceQuery<String, String, String> q = createSuperSliceQuery(ko, se, se, se);
    q.setColumnFamily(cf);
    q.setKey("testSuperSliceQuery");
    // try with column name first
    q.setColumnNames("testSuperSliceQuery1", "testSuperSliceQuery2", "testSuperSliceQuery3");
    Result<SuperSlice<String, String, String>> r = q.execute();
    assertNotNull(r);
    SuperSlice<String, String, String> slice = r.get();
    assertNotNull(slice);
    assertEquals(3, slice.getSuperColumns().size());
    // Test slice.getColumnByName
    assertEquals("value", slice.getColumnByName("testSuperSliceQuery1").getColumns().get(0)
                               .getValue());

    // now try with start/finish
    q = createSuperSliceQuery(ko, se, se, se);
    q.setColumnFamily(cf);
    q.setKey("testSuperSliceQuery");
    // try reversed this time
    q.setRange("testSuperSliceQuery1", "testSuperSliceQuery2", false, 2);
    r = q.execute();
    assertNotNull(r);
    slice = r.get();
    assertNotNull(slice);
    for (HSuperColumn<String, String, String> scolumn : slice.getSuperColumns()) {
      if (!scolumn.getName().equals("testSuperSliceQuery1")
          && !scolumn.getName().equals("testSuperSliceQuery2")) {
        fail("A columns with unexpected column name returned: " + scolumn.getName());
      }
    }

    // Delete values
    for (int j = 1; j <= 3; ++j) {
      m.addDeletion("testSuperSliceQuery", cf, "testSuperSliceQuery" + j, se);
    }
    mr = m.execute();

    // Test after deletion
    r = q.execute();
    assertNotNull(r);
    slice = r.get();
    assertNotNull(slice);
    assertTrue(slice.getSuperColumns().isEmpty());
  }

  /**
   * Tests the SubSliceQuery, a query on columns within a supercolumn
   */
  @Test
  public void testSubSliceQuery() {
    String cf = "Super1";

    // insert
    TestCleanupDescriptor cleanup = createSuperColumns(cf, 1, "testSliceQueryOnSubcolumns", 1,
        "testSliceQueryOnSubcolumns_column");

    // get value
    SubSliceQuery<String, String, String> q = createSubSliceQuery(ko, se, se, se);
    q.setColumnFamily(cf);
    q.setSuperColumn("testSliceQueryOnSubcolumns_column0");
    q.setKey("testSliceQueryOnSubcolumns0");
    // try with column name first
    q.setColumnNames("c000", "c110", "c_doesn't_exist");
    Result<ColumnSlice<String, String>> r = q.execute();
    assertNotNull(r);
    ColumnSlice<String, String> slice = r.get();
    assertNotNull(slice);
    assertEquals(2, slice.getColumns().size());
    // Test slice.getColumnByName
    assertEquals("v000", slice.getColumnByName("c000").getValue());

    // now try with start/finish
    q = createSubSliceQuery(ko, se, se, se);
    q.setColumnFamily(cf);
    q.setKey("testSliceQueryOnSubcolumns0");
    q.setSuperColumn("testSliceQueryOnSubcolumns_column0");
    // try reversed this time
    q.setRange("c000", "c110", false, 2);
    r = q.execute();
    assertNotNull(r);
    slice = r.get();
    assertNotNull(slice);
    for (HColumn<String, String> column : slice.getColumns()) {
      if (!column.getName().equals("c000") && !column.getName().equals("c110")) {
        fail("A columns with unexpected column name returned: " + column.getName());
      }
    }

    // Delete values
    deleteColumns(cleanup);

    // Test after deletion
    r = q.execute();
    assertNotNull(r);
    slice = r.get();
    assertNotNull(slice);
    assertTrue(slice.getColumns().isEmpty());
  }

  @Test
  public void testMultigetSuperSliceQuery() {
    String cf = "Super1";

    TestCleanupDescriptor cleanup = createSuperColumns(cf, 4, "testSuperMultigetSliceQueryKey", 3,
        "testSuperMultigetSliceQuery");

    // get value
    MultigetSuperSliceQuery<String, String, String> q = createMultigetSuperSliceQuery(ko, se, se,
        se);
    q.setColumnFamily(cf);
    q.setKeys("testSuperMultigetSliceQueryKey0", "testSuperMultigetSliceQueryKey3");
    // try with column name first
    q.setColumnNames("testSuperMultigetSliceQuery1", "testSuperMultigetSliceQuery2");
    Result<SuperRows<String, String, String>> r = q.execute();
    assertNotNull(r);
    SuperRows<String, String, String> rows = r.get();
    assertNotNull(rows);
    assertEquals(2, rows.getCount());
    SuperRow<String, String, String> row = rows.getByKey("testSuperMultigetSliceQueryKey0");
    assertNotNull(row);
    assertEquals("testSuperMultigetSliceQueryKey0", row.getKey());
    SuperSlice<String, String, String> slice = row.getSuperSlice();
    assertNotNull(slice);
    // Test slice.getColumnByName
    assertEquals("v001", slice.getColumnByName("testSuperMultigetSliceQuery1").getColumns().get(0)
                              .getValue());
    assertNull(slice.getColumnByName("testSuperMultigetSliceQuery3"));

    deleteColumns(cleanup);
  }

  @Test
  public void testMultigetSubSliceQuery() {
    String cf = "Super1";

    // insert
    TestCleanupDescriptor cleanup = createSuperColumns(cf, 3, "testMultigetSubSliceQuery", 1,
        "testMultigetSubSliceQuery");

    // get value
    MultigetSubSliceQuery<String, String, String> q = createMultigetSubSliceQuery(ko, se, se, se);
    q.setColumnFamily(cf);
    q.setSuperColumn("testMultigetSubSliceQuery0");
    q.setKeys("testMultigetSubSliceQuery0", "testMultigetSubSliceQuery2");
    // try with column name first
    q.setColumnNames("c000", "c110");
    Result<Rows<String, String>> r = q.execute();
    assertNotNull(r);
    Rows<String, String> rows = r.get();
    assertNotNull(rows);
    assertEquals(2, rows.getCount());
    Row<String, String> row = rows.getByKey("testMultigetSubSliceQuery0");
    assertNotNull(row);
    assertEquals("testMultigetSubSliceQuery0", row.getKey());
    ColumnSlice<String, String> slice = row.getColumnSlice();
    assertNotNull(slice);
    // Test slice.getColumnByName
    assertEquals("v000", slice.getColumnByName("c000").getValue());
    assertEquals("v100", slice.getColumnByName("c110").getValue());
    // Test slice.getColumns
    List<HColumn<String, String>> columns = slice.getColumns();
    assertNotNull(columns);
    assertEquals(2, columns.size());

    // now try with start/finish
    q = createMultigetSubSliceQuery(ko, se, se, se);
    q.setColumnFamily(cf);
    q.setKeys("testMultigetSubSliceQuery0");
    q.setSuperColumn("testMultigetSubSliceQuery0");
    // try reversed this time
    q.setRange("c000", "c110", false, 2);
    r = q.execute();
    assertNotNull(r);
    rows = r.get();
    assertEquals(1, rows.getCount());
    for (Row<String, String> row2 : rows) {
      assertNotNull(row2);
      slice = row2.getColumnSlice();
      assertNotNull(slice);
      assertEquals(2, slice.getColumns().size());
      for (HColumn<String, String> column : slice.getColumns()) {
        if (!column.getName().equals("c000") && !column.getName().equals("c110")) {
          fail("A columns with unexpected column name returned: " + column.getName());
        }
      }
    }

    // Delete values
    deleteColumns(cleanup);
  }

  @Test
  @Ignore("Not ready yet")
  public void testRangeSlicesQuery() {
    // TODO
  }

  @Test
  @Ignore("Not ready yet")
  public void testRangeSuperSlicesQuery() {
    // TODO
  }

  @Test
  @Ignore("Not ready yet")
  public void testRangeSubSlicesQuery() {
    // TODO
  }

  private void deleteColumns(TestCleanupDescriptor cleanup) {
    Mutator m = createMutator(ko);
    for (int i = 0; i < cleanup.rowCount; ++i) {
      for (int j = 0; j < cleanup.columnCount; ++j) {
        m.addDeletion(cleanup.rowPrefix + i, cleanup.cf, cleanup.columnsPrefix + j, se);
      }
    }
    m.execute();
  }

  private TestCleanupDescriptor createSuperColumns(String cf, int rowCount, String rowPrefix,
      int scCount, String scPrefix) {
    Mutator m = createMutator(ko);
    for (int i = 0; i < rowCount; ++i) {
      for (int j = 0; j < scCount; ++j) {
        @SuppressWarnings("unchecked")
        HSuperColumn<String, String, String> sc = createSuperColumn(
            scPrefix + j,
            Arrays.asList(createColumn("c0" + i + j, "v0" + i + j, se, se),
                createColumn("c1" + 1 + j, "v1" + i + j, se, se)), se, se, se);
        m.addInsertion(rowPrefix + i, cf, sc);
      }
    }
    m.execute();
    return new TestCleanupDescriptor(cf, rowCount, rowPrefix, scCount, scPrefix);
  }

  private TestCleanupDescriptor insertColumns(String cf, int rowCount, String rowPrefix,
      int columnCount, String columnPrefix) {
    Mutator m = createMutator(ko);
    for (int i = 0; i < rowCount; ++i) {
      for (int j = 0; j < columnCount; ++j) {
        m.addInsertion(rowPrefix + i, cf,
            createColumn(columnPrefix + j, "value" + i + j, se, se));
      }
    }
    MutationResult mr = m.execute();
    assertTrue("Time should be > 0", mr.getExecutionTimeMicro() > 0);
    log.debug("insert execution time: {}", mr.getExecutionTimeMicro());
    return new TestCleanupDescriptor(cf, rowCount, rowPrefix, columnCount, columnPrefix);
  }

  /**
   * A class describing what kind of cleanup is required at the end of the test. Just some
   * bookeeping, that's all.
   *
   * @author Ran Tavory
   *
   */
  private static class TestCleanupDescriptor {
    public final String cf;
    public final int rowCount;
    public final String rowPrefix;
    public final int columnCount;
    public final String columnsPrefix;

    public TestCleanupDescriptor(String cf, int rowCount, String rowPrefix, int scCount,
        String scPrefix) {
      this.cf = cf;
      this.rowCount = rowCount;
      this.rowPrefix = rowPrefix;
      this.columnCount = scCount;
      this.columnsPrefix = scPrefix;
    }
  }
}