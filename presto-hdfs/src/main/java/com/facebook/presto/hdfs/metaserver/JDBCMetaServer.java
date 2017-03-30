/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.hdfs.metaserver;

import com.facebook.presto.hdfs.HDFSColumnHandle;
import com.facebook.presto.hdfs.HDFSConfig;
import com.facebook.presto.hdfs.HDFSDatabase;
import com.facebook.presto.hdfs.HDFSTableHandle;
import com.facebook.presto.hdfs.HDFSTableLayoutHandle;
import com.facebook.presto.hdfs.StorageFormat;
import com.facebook.presto.hdfs.exception.ColTypeNonValidException;
import com.facebook.presto.hdfs.exception.ColumnNotFoundException;
import com.facebook.presto.hdfs.exception.MetaServerCorruptionException;
import com.facebook.presto.hdfs.exception.RecordMoreLessException;
import com.facebook.presto.hdfs.exception.TypeUnknownException;
import com.facebook.presto.hdfs.exception.UnSupportedFunctionException;
import com.facebook.presto.hdfs.fs.FSFactory;
import com.facebook.presto.hdfs.function.Function;
import com.facebook.presto.hdfs.function.Function0;
import com.facebook.presto.hdfs.jdbc.JDBCDriver;
import com.facebook.presto.hdfs.jdbc.JDBCRecord;
import com.facebook.presto.hdfs.type.UnknownType;
import com.facebook.presto.spi.ColumnMetadata;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.SchemaTablePrefix;
import com.facebook.presto.spi.type.BigintType;
import com.facebook.presto.spi.type.BooleanType;
import com.facebook.presto.spi.type.CharType;
import com.facebook.presto.spi.type.DateType;
import com.facebook.presto.spi.type.DecimalType;
import com.facebook.presto.spi.type.DoubleType;
import com.facebook.presto.spi.type.IntegerType;
import com.facebook.presto.spi.type.RealType;
import com.facebook.presto.spi.type.SmallintType;
import com.facebook.presto.spi.type.TimeType;
import com.facebook.presto.spi.type.TimestampType;
import com.facebook.presto.spi.type.TinyintType;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.VarcharType;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * @author jelly.guodong.jin@gmail.com
 */
public class JDBCMetaServer
implements MetaServer
{
    private static final Logger log = Logger.get(JDBCMetaServer.class);
    private static final Map<String, String> sqlTable = new HashMap<>();

    private final JDBCDriver jdbcDriver;
    private final HDFSConfig config;

    private FileSystem fileSystem;

    // read config. check if meta table already exists in database, or else initialize tables.
    @Inject
    public JDBCMetaServer(HDFSConfig config, FSFactory fsFactory)
    {
        this.config = config;
        // initialize a jdbc driver
        jdbcDriver = new JDBCDriver(
                config.getJdbcDriver(),
                config.getMetaserverUser(),
                config.getMetaserverPass(),
                config.getMetaserverUri());

        log.debug("MetaServer Config: " + config.getJdbcDriver()
            + "; "
            + config.getMetaserverUri()
            + "; "
            + config.getMetaserverUser()
            + "; "
            + config.getMetaserverPass()
            + "; "
            + config.getMetaserverStore());

        sqlTable.putIfAbsent("dbs",
                "CREATE TABLE DBS(ID BIGSERIAL PRIMARY KEY, NAME varchar(128) UNIQUE, LOCATION varchar(1000));");
        sqlTable.putIfAbsent("tbls",
                "CREATE TABLE TBLS(ID BIGSERIAL PRIMARY KEY, DB_ID BIGINT, NAME varchar(128), DB_NAME varchar(128), LOCATION varchar(1000), STORAGE INT, FIB_K varchar(128), FIB_FUNC varchar(20), TIME_K varchar(128)); CREATE UNIQUE INDEX tableunique ON TBLS(NAME, DB_ID);");
        // COL_NAME: TBL_NAME.col_name;  COL_TYPE: FIBER_COL|TIME_COL|REGULAR;   TYPE:INT|DECIMAL|STRING|... refer to spi.StandardTypes
        sqlTable.putIfAbsent("cols",
                "CREATE TABLE COLS(ID BIGSERIAL PRIMARY KEY, NAME varchar(128), TBL_ID BIGINT, TBL_NAME varchar(128), DB_NAME varchar(128), DATA_TYPE varchar(128), COL_TYPE varchar(128)); CREATE UNIQUE INDEX colunique ON COLS(NAME, TBL_ID);");
        sqlTable.putIfAbsent("fibers",
                "CREATE TABLE FIBERS(ID BIGSERIAL PRIMARY KEY, TBL_ID BIGINT, FIBER_V bigint); CREATE UNIQUE INDEX fibersunique ON FIBERS(TBL_ID, FIBER_V);");
        sqlTable.putIfAbsent("fiber_time",
                "CREATE TABLE FIBERFILES(ID BIGSERIAL PRIMARY KEY, FIBER_ID BIGINT, TIME_B timestamp, TIME_E timestamp, PATH varchar(1024) UNIQUE);");
        fileSystem = fsFactory.getFS().get();

        // initialise meta tables
        initMeta();
    }

    // check if meta tables already exist, if not, create them.
    private void initMeta()
    {
        log.debug("Init meta data in jdbc meta store");
        int initFlag = 0;
        DatabaseMetaData dbmeta = jdbcDriver.getDbMetaData();
        if (dbmeta == null) {
            log.error("database meta is null");
            // TODO create database meta
        }
        for (String tbl : sqlTable.keySet()) {
            assert dbmeta != null;
            try (ResultSet rs = dbmeta.getTables(null, null, tbl, null)) {
                // if table exists
                if (rs.next()) {
                    initFlag++;
                    log.info("Table " + tbl + " already exists.");
                }
            } catch (SQLException e) {
                log.error(e, "jdbc meta getTables error");
            }
        }
        // if no table exists, init all
        if (initFlag == 0) {
            sqlTable.keySet().forEach(
                    tbl -> {
                        if (jdbcDriver.executeUpdate(sqlTable.get(tbl)) == 0) {
                            log.info("Create table" + tbl + " in metaserver");
                        }
                        else {
                            log.error(tbl + " table creation in metaserver execution failed.");
                        }
                    }
            );
            HDFSDatabase defaultDB = new HDFSDatabase("default");
            createDatabase(defaultDB);
        }
        // if not all tables exist, throw an error and break
        else if (initFlag != sqlTable.keySet().size()) {
            log.error("Tables not complete!");
            throw new MetaServerCorruptionException("Tables are corrupted");
        }
    }

    @Override
    public List<String> getAllDatabases()
    {
        log.debug("Get all databases");
        List<JDBCRecord> records;
        List<String> resultL = new ArrayList<>();
        String sql = "SELECT name FROM dbs;";
        String[] fields = {"name"};
        records = jdbcDriver.executreQuery(sql, fields);
        records.forEach(record -> resultL.add(record.getString(fields[0])));
        return resultL;
    }

    private String getDatabaseId(String db)
    {
        log.debug("Get database " + db);

        String sql = "SELECT id FROM dbs WHERE name='" + db + "';";
        String[] fields = {"id"};
        List<JDBCRecord> records = jdbcDriver.executreQuery(sql, fields);

        if (records.isEmpty()) {
            log.debug("Find no database with name " + db);
            return null;
        }

        return records.get(0).getString("id");
    }

    @Override
    public List<SchemaTableName> listTables(SchemaTablePrefix prefix)
    {
        log.debug("List all tables with prefix " + prefix.toString());
        List<JDBCRecord> records;
        List<SchemaTableName> tables = new ArrayList<>();
        String dbPrefix = prefix.getSchemaName();
        log.debug("listTables dbPrefix: " + dbPrefix);
        String tblPrefix = prefix.getTableName();
        log.debug("listTables tblPrefix: " + tblPrefix);
        StringBuilder baseSql = new StringBuilder("SELECT name, db_name FROM tbls");
        // if dbPrefix not mean to match all
        if (dbPrefix != null) {
            baseSql.append(" WHERE db_name='").append(dbPrefix).append("'");
            // if tblPrefix not mean to match all
            if (tblPrefix != null) {
                baseSql.append(" AND name='").append(tblPrefix).append("'");
            }
        }
        baseSql.append(";");
        String tableName;
        String dbName;
        String[] fields = {"name, db_name"};
        records = jdbcDriver.executreQuery(baseSql.toString(), fields);
        if (records.size() == 0) {
            return tables;
        }
        for (JDBCRecord record : records) {
            tableName = record.getString(fields[0]);
            dbName = record.getString(fields[1]);
            log.debug("listTables tableName: " + formName(dbName, tableName));
            tables.add(new SchemaTableName(dbName, tableName));
        }
        return tables;
    }

    private String getTableId(String databaseName, String tableName)
    {
        String sql = "SELECT id FROM tbls WHERE name='" + tableName + "' AND db_name='" + databaseName + "';";
        String[] fields = {"id"};
        List<JDBCRecord> records = jdbcDriver.executreQuery(sql, fields);
        if (records.isEmpty()) {
            return null;
        }
        return records.get(0).getString("id");
    }

//    private Optional<HDFSTable> getTable(String databaseName, String tableName)
//    {
//        log.debug("Get table " + formName(databaseName, tableName));
//        HDFSTableHandle table;
//        HDFSTableLayoutHandle tableLayout;
//        List<HDFSColumnHandle> columns;
//        List<ColumnMetadata> metadatas;
//
//        Optional<HDFSTableHandle> tableOptional = getTableHandle(databaseName, tableName);
//        if (!tableOptional.isPresent()) {
//            log.warn("Table not exists");
//            return Optional.empty();
//        }
//        table = tableOptional.get();
//
//        Optional<HDFSTableLayoutHandle> tableLayoutOptional = getTableLayout(databaseName, tableName);
//        if (!tableLayoutOptional.isPresent()) {
//            log.warn("Table layout not exists");
//            return Optional.empty();
//        }
//        tableLayout = tableLayoutOptional.get();
//
//        Optional<List<HDFSColumnHandle>> columnsOptional = getTableColumnHandle(databaseName, tableName);
//        if (!columnsOptional.isPresent()) {
//            log.warn("Column handles not exists");
//            return Optional.empty();
//        }
//        columns = columnsOptional.get();
//
//        Optional<List<ColumnMetadata>> metadatasOptional = getTableColMetadata(databaseName, tableName);
//        if (!metadatasOptional.isPresent()) {
//            log.warn("Column metadata not exists");
//            return Optional.empty();
//        }
//        metadatas = metadatasOptional.get();
//
//        HDFSTable hdfsTable = new HDFSTable(table, tableLayout, columns, metadatas);
//        return Optional.of(hdfsTable);
//    }

    @Override
    public Optional<HDFSTableHandle> getTableHandle(String connectorId, String databaseName, String tableName)
    {
        log.debug("Get table handle " + formName(databaseName, tableName));
        HDFSTableHandle table;
        List<JDBCRecord> records;
        String sql = "SELECT name, location FROM tbls WHERE db_name='"
                + databaseName
                + "' AND name='"
                + tableName
                + "';";
        String[] fields = {"name", "db_name", "location"};
        records = jdbcDriver.executreQuery(sql, fields);
        if (records.size() != 1) {
            log.error("Match more/less than one table");
            return Optional.empty();
        }
        JDBCRecord record = records.get(0);
        String name = record.getString(fields[0]);
        String schema = record.getString(fields[1]);
        String location = record.getString(fields[2]);
        table = new HDFSTableHandle(
                requireNonNull(connectorId, "connectorId is null"),
                requireNonNull(schema, "database name is null"),
                requireNonNull(name, "table name is null"),
                requireNonNull(new Path(location), "location uri is null"));
        return Optional.of(table);
    }

    @Override
    public Optional<HDFSTableLayoutHandle> getTableLayout(String connectorId, String databaseName, String tableName)
    {
        // TODO add fiberCol, timeCol, fiberFunc
        log.debug("Get table layout " + formName(databaseName, tableName));
        HDFSTableLayoutHandle tableLayout;
        List<JDBCRecord> records;
        String sql = "SELECT fib_k, time_k, fib_func FROM tbls WHERE name='"
                + tableName
                + "' AND db_name='"
                + databaseName
                + "';";
        String[] fields = {"fib_k", "time_k", "fib_func"};
        records = jdbcDriver.executreQuery(sql, fields);
        if (records.size() != 1) {
            log.error("Match more/less than one table");
            return Optional.empty();
        }
        HDFSTableHandle tableHandle = getTableHandle(connectorId, databaseName, tableName).orElse(null);
        if (tableHandle == null) {
            log.error("Match no table handle");
            return Optional.empty();
        }

        JDBCRecord record = records.get(0);
        String fiberColName = record.getString(fields[0]);
        String timeColName = record.getString(fields[1]);
        String fiberFunc = record.getString(fields[2]);
        records.clear();

        Function function = parseFunction(fiberFunc);
        if (function == null) {
            log.error("Function parse error");
            return Optional.empty();
        }

        // construct ColumnHandle
        HDFSColumnHandle fiberCol = getColumnHandle(connectorId, databaseName, tableName, fiberColName);
        HDFSColumnHandle timeCol = getColumnHandle(connectorId, databaseName, tableName, timeColName);

        tableLayout = new HDFSTableLayoutHandle(tableHandle, fiberCol, timeCol, function, StorageFormat.PARQUET);
        return Optional.of(tableLayout);
    }

    /**
     * Get all column handles of specified table
     * */
    @Override
    public Optional<List<HDFSColumnHandle>> getTableColumnHandle(String connectorId, String databaseName, String tableName)
    {
        log.debug("Get list of column handles of table " + formName(databaseName, tableName));
        List<HDFSColumnHandle> columnHandles = new ArrayList<>();
        List<JDBCRecord> records;
        String colName;
        String colTypeName;
        String dataTypeName;
        String sql = "SELECT name, col_type, data_type FROM cols WHERE tbl_name='"
                + tableName
                + "' AND db_name='"
                + databaseName
                + "';";
        String[] colFields = {"name, col_type, data_type"};
        records = jdbcDriver.executreQuery(sql, colFields);
        if (records.size() == 0) {
            log.warn("No col matches!");
            return Optional.empty();
        }
        for (JDBCRecord record : records) {
            colName = record.getString(colFields[0]);
            colTypeName = record.getString(colFields[1]);
            dataTypeName = record.getString(colFields[2]);

            // Deal with col type
            HDFSColumnHandle.ColumnType colType = getColType(colTypeName);
            if (colType == HDFSColumnHandle.ColumnType.NOTVALID) {
                log.error("Col type non valid!");
                throw new ColTypeNonValidException();
            }
            // Deal with data type
            Type type = getType(dataTypeName);
            if (type == UnknownType.UNKNOWN) {
                log.error("Type unknown!");
                throw new TypeUnknownException();
            }

            columnHandles.add(new HDFSColumnHandle(colName, type, "", colType, connectorId));
        }
        return Optional.of(columnHandles);
    }

    @Override
    public void shutdown()
    {
        jdbcDriver.shutdown();
    }

    private HDFSColumnHandle getColumnHandle(String connectorId, String colName, String tableName, String dbName)
    {
        List<JDBCRecord> records;
        String sql = "SELECT col_type, data_type FROM cols WHERE name='"
                + colName
                + "' AND tbl_name='"
                + tableName
                + "' AND db_name='"
                + dbName
                + "';";
        String[] colFields = {"col_type", "data_type"};
        records = jdbcDriver.executreQuery(sql, colFields);
        if (records.size() != 1) {
            log.error("Match more/less than one table");
            throw new RecordMoreLessException();
        }
        JDBCRecord fiberColRecord = records.get(0);
        String colTypeName = fiberColRecord.getString(colFields[0]);
        String typeName = fiberColRecord.getString(colFields[1]);
        records.clear();
        // Deal with colType
        HDFSColumnHandle.ColumnType colType = getColType(colTypeName);
        if (colType == HDFSColumnHandle.ColumnType.NOTVALID) {
            log.error("Col type non valid!");
            throw new ColTypeNonValidException();
        }
        // Deal with type
        Type type = getType(typeName);
        if (type == UnknownType.UNKNOWN) {
            log.error("Type unknown!");
            throw new TypeUnknownException();
        }
        return new HDFSColumnHandle(colName, type, "", colType, connectorId);
    }

    public Optional<List<ColumnMetadata>> getTableColMetadata(String connectorId, String databaseName, String tableName)
    {
        log.debug("Get list of column metadata of table " + formName(databaseName, tableName));
        List<ColumnMetadata> colMetadatas = new ArrayList<>();
        List<JDBCRecord> records;
        String sql = "SELECT name, type, tbl_name, db_name FROM cols WHERE tbl_name='"
                + tableName
                + "' AND db_name='"
                + databaseName
                + "';";
        String[] colFields = {"name, type, tbl_name, db_name"};
        records = jdbcDriver.executreQuery(sql, colFields);
        if (records.size() == 0) {
            log.warn("No col matches!");
            return Optional.empty();
        }
        records.forEach(record -> {
            String typeName = record.getString(colFields[1]);
            Type type = getType(typeName);
            if (type == UnknownType.UNKNOWN) {
                log.error("Type unknown!");
                throw new TypeUnknownException();
            }
            ColumnMetadata metadata = new ColumnMetadata(
                    record.getString(colFields[0]),
                    type,
                    "",
                    false);
            colMetadatas.add(metadata);
        });
        return Optional.of(colMetadatas);
    }

//    private ColumnMetadata getColMetadata(String colName, String tblName, String dbName)
//    {
//        log.debug("Get metadata of col " + formName(dbName, tblName, colName));
//        List<JDBCRecord> records;
//        String sql = "SELECT type FROM cols WHERE name='"
//                + colName
//                + "' AND tbl_name='"
//                + tblName
//                + " AND db_name='"
//                + dbName
//                + "';";
//        String[] colFields = {"type"};
//        records = jdbcDriver.executreQuery(sql, colFields);
//        if (records.size() != 1) {
//            log.error("Match more/less than one table");
//            throw new RecordMoreLessException();
//        }
//        JDBCRecord colRecord = records.get(0);
//        String typeName = colRecord.getString(colFields[0]);
//        Type type = getType(typeName);
//        if (type == UnknownType.UNKNOWN) {
//            log.error("Type unknown!");
//            throw new TypeUnknownException();
//        }
//        return new ColumnMetadata(colName, type, "", false);
//    }

    @Override
    public void createDatabase(ConnectorSession session, HDFSDatabase database)
    {
        createDatabase(database);
    }

    private void createDatabase(HDFSDatabase database)
    {
        database.setLocation(formPath(database.getName()).toString());
        log.debug("Create database " + database.getName());
        createDatabase(database.getName(),
                database.getLocation());
    }

    private void createDatabase(String dbName, String dbPath)
    {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO dbs(name, location) VALUES('")
                .append(dbName)
                .append("', '")
                .append(dbPath)
                .append("');");
        if (jdbcDriver.executeUpdate(sql.toString()) != 0) {
            // create hdfs dir
            try {
                log.debug("Sql: " + sql.toString());
                log.debug("Create hdfs dir at " + dbPath);
                fileSystem.mkdirs(new Path(dbPath));
            }
            catch (IOException e) {
                log.error(e);
            }
        }
        else {
            log.error("Create database" + dbName + " failed!");
        }
    }

    @Override
    public void createTable(ConnectorSession session, ConnectorTableMetadata tableMetadata)
    {
        log.debug("Create table " + tableMetadata.getTable().getTableName());
        String tableName = tableMetadata.getTable().getTableName();
        String schemaName = tableMetadata.getTable().getSchemaName();
        List<ColumnMetadata> columns = tableMetadata.getColumns();
        String dbId = getDatabaseId(schemaName);

        if (dbId == null) {
            log.debug("No database with name " + schemaName + " found during creating table.");
            return;
        }

        // get some more properties
        String location = formPath(schemaName, tableName).toString();
        String fiberCol = "";
        String timeCol = "";
        String fiberFunc = "function0";

        createTable(columns, dbId, tableName, schemaName, location, "parquet", fiberCol, fiberFunc, timeCol);
    }

    @Override
    public void createTableWithFiber(ConnectorSession session, ConnectorTableMetadata tableMetadata, String fiberKey, String function, String timeKey)
    {
        log.debug("Create table with fiber " + tableMetadata.getTable().getTableName());
        // check fiberKey, function and timeKey
        List<ColumnMetadata> columns = tableMetadata.getColumns();
        List<String> columnNames = columns.stream()
                .map(ColumnMetadata::getName)
                .collect(Collectors.toList());
        if (!columnNames.contains(fiberKey) || !columnNames.contains(timeKey)) {
            log.error("Fiber key or timestamp key not exist in table columns");
            throw new ColumnNotFoundException("");
        }
        if (parseFunction(function) == null) {
            log.error("Function not exists");
            throw new UnSupportedFunctionException(function);
        }

        String tableName = tableMetadata.getTable().getTableName();
        String schemaName = tableMetadata.getTable().getSchemaName();
        String dbId = getDatabaseId(schemaName);
        if (dbId == null) {
            log.debug("No database with name " + schemaName + " found during creating table.");
            // TODO throw exception
            return;
        }

        String location = formPath(schemaName, tableName).toString();
        // createTable
        createTable(columns, dbId, tableName, schemaName, location, "parquet", fiberKey, function, timeKey);
    }

    private void createTable(List<ColumnMetadata> columns, String dbId, String name, String dbName, String location, String storage, String fiberKey, String function, String timeKey)
    {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO tbls(db_id, name, db_name, location, storage, fib_k, fib_func, time_k) VALUES('")
                .append(dbId)
                .append("', '")
                .append(name)
                .append("', '")
                .append(dbName)
                .append("', '")
                .append(location)
                .append("', '")
                .append(storage)
                .append("', '")
                .append(fiberKey)
                .append("', '")
                .append(function)
                .append("', '")
                .append(timeKey)
                .append("');");
        if (jdbcDriver.executeUpdate(sql.toString()) != 0) {
            try {
                log.debug("Create hdfs dir for " + formName(dbName, name));
                fileSystem.mkdirs(formPath(dbName, name));
            }
            catch (IOException e) {
                log.debug("Error sql: " + sql.toString());
                log.error(e);
                // TODO exit ?
            }
        }
        else {
            log.error("Create table " + formName(dbName, name) + " failed!");
        }

        String tableId = getTableId(dbName, name);

        // add cols information
        for (ColumnMetadata col : columns) {
            String colType = "regular";
            if (Objects.equals(col.getName(), fiberKey)) {
                colType = "fiber";
            }
            if (Objects.equals(col.getName(), timeKey)) {
                colType = "time";
            }
            sql.delete(0, sql.length() - 1);
            sql.append("INSERT INTO cols(name, tbl_id, tbl_name, db_name, data_type, col_type) VALUES('")
                    .append(col.getName())
                    .append("', '")
                    .append(tableId)
                    .append("', '")
                    .append(name)
                    .append("', '")
                    .append(dbName)
                    .append("', '")
                    .append(col.getType())
                    .append("', '")
                    .append(colType)
                    .append("');");
            if (jdbcDriver.executeUpdate(sql.toString()) == 0) {
                log.debug("Error sql: " + sql.toString());
                log.error("Create cols for table " + formName(dbName, name) + " failed!");
                // TODO exit ?
            }
        }
    }

    private HDFSColumnHandle.ColumnType getColType(String typeName)
    {
        log.debug("Get col type " + typeName);
        switch (typeName.toUpperCase()) {
            case "FIBER_COL": return HDFSColumnHandle.ColumnType.FIBER_COL;
            case "TIME_COL": return HDFSColumnHandle.ColumnType.TIME_COL;
            case "REGULAR": return HDFSColumnHandle.ColumnType.REGULAR;
            default : log.error("No match col type!");
                return HDFSColumnHandle.ColumnType.NOTVALID;
        }
    }

    private Type getType(String typeName)
    {
        log.debug("Get type " + typeName);
        typeName = typeName.toLowerCase();
        // check if type is varchar(xx)
        Pattern vcpattern = Pattern.compile("varchar\\(\\s*(\\d+)\\s*\\)");
        Matcher vcmatcher = vcpattern.matcher(typeName);
        if (vcmatcher.find()) {
            String vlen = vcmatcher.group(1);
            if (!vlen.isEmpty()) {
                return VarcharType.createVarcharType(Integer.parseInt(vlen));
            }
            return UnknownType.UNKNOWN;
        }
        // check if type is char(xx)
        Pattern cpattern = Pattern.compile("char\\(\\s*(\\d+)\\s*\\)");
        Matcher cmatcher = cpattern.matcher(typeName);
        if (cmatcher.find()) {
            String clen = cmatcher.group(1);
            if (!clen.isEmpty()) {
                return CharType.createCharType(Integer.parseInt(clen));
            }
            return UnknownType.UNKNOWN;
        }
        // check if type is decimal(precision, scale)
        Pattern dpattern = Pattern.compile("decimal\\((\\d+)\\s*,?\\s*(\\d*)\\)");
        Matcher dmatcher = dpattern.matcher(typeName);
        if (dmatcher.find()) {
            String dprecision = dmatcher.group(1);
            String dscale = dmatcher.group(2);
            if (dprecision.isEmpty()) {
                return UnknownType.UNKNOWN;
            }
            if (dscale.isEmpty()) {
                return DecimalType.createDecimalType(Integer.parseInt(dprecision));
            }
            return DecimalType.createDecimalType(Integer.parseInt(dprecision), Integer.parseInt(dscale));
        }
        switch (typeName) {
            case "boolean": return BooleanType.BOOLEAN;
            case "tinyint": return TinyintType.TINYINT;
            case "smallint": return SmallintType.SMALLINT;
            case "integer": return IntegerType.INTEGER;
            case "bigint": return BigintType.BIGINT;
            case "real": return RealType.REAL;
            case "double": return DoubleType.DOUBLE;
            case "date": return DateType.DATE;
            case "time": return TimeType.TIME;
            case "timestamp": return TimestampType.TIMESTAMP;
            default: return UnknownType.UNKNOWN;
        }
    }

    private Function parseFunction(String function)
    {
        switch (function) {
            case "function0": return new Function0();
        }
        return null;
    }

    // form concatenated name from database and table
    private String formName(String database, String table)
    {
        return database + "." + table;
    }

    private Path formPath(String dirOrFile1, String dirOrFile2)
    {
        String base = config.getMetaserverStore();
        String path1 = dirOrFile1;
        String path2 = dirOrFile2;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 2);
        }
        if (!path1.startsWith("/")) {
            path1 = "/" + path1;
        }
        if (path1.endsWith("/")) {
            path1 = path1.substring(0, path1.length() - 2);
        }
        if (!path2.startsWith("/")) {
            path2 = "/" + path2;
        }
        return Path.mergePaths(Path.mergePaths(new Path(base), new Path(path1)), new Path(path2));
    }

    private Path formPath(String dirOrFile)
    {
        String base = config.getMetaserverStore();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 2);
        }
        if (!dirOrFile.startsWith("/")) {
            dirOrFile = "/" + dirOrFile;
        }
        return Path.mergePaths(new Path(base), new Path(dirOrFile));
    }
}
