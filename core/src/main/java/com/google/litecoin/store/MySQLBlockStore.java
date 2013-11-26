package com.google.litecoin.store;



import com.google.litecoin.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.sql.*;

/**
 * A block store using the Apache MySQLB pure-java embedded database.
 *
 * @author miron@google.com (Miron Cuperman)
 */
public class MySQLBlockStore implements BlockStore {
    private static final int COMMIT_INTERVAL = 2 * 1000;

    private static final Logger log = LoggerFactory.getLogger(MySQLBlockStore.class);

    private StoredBlock chainHeadBlock;
    private Sha256Hash chainHeadHash;
    private NetworkParameters params;
    private Connection conn;

    private String dbName;

    private Thread committerThread;

    //static final String driver = "org.mariadb.jdbc.Driver";
    static final String driver = "com.mysql.jdbc.Driver";

    static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings ( "
            + "name VARCHAR(32) NOT NULL,"
            + "value BLOB,"
            + "PRIMARY KEY settings_pk(`name`)"
            + ")";
    static final String CREATE_BLOCKS_TABLE = "CREATE TABLE blocks ( "
            + "hash CHAR(32) NOT NULL,"
            + "chainWork BLOB NOT NULL,"
            + "height BIGINT NOT NULL,"
            + "header BLOB NOT NULL,"
            + "PRIMARY KEY blocks_pk(`hash`)"
            + ")";
    static final String CHAIN_HEAD_SETTING = "chainhead";

    public static void main(String[] args) throws Exception {
        //MySQLBlockStore store = new MySQLBlockStore(NetworkParameters.testNet(), ".bitcoinj-test.MySQL");
        //store.resetStore();
    }

    public synchronized void close() {
       // String connectionURL = "jdbc:mysql:" + dbName + ";shutdown=true";
        try {
            if (conn != null) {
                conn.commit();
                conn = null;
            }
            if (committerThread != null)
                committerThread.interrupt();
            conn.close();

        } catch (Exception ex) {
            log.error("close failed", ex);
        }
    }

    private synchronized void commit() throws BlockStoreException {
        try {
            if (conn != null)
                conn.commit();
        } catch (SQLException ex) {
            log.error("commit failed", ex);
            throw new BlockStoreException(ex);
        }
    }

    public MySQLBlockStore(NetworkParameters params, String dbName) throws BlockStoreException {
        this.params = params;
        this.dbName = dbName;
        String connectionURL = "jdbc:mysql://" + dbName ;//+"&dontTrackOpenResources=true";// + ";create=true";

        try {
            Class.forName(driver).newInstance();
            log.info(driver + " loaded. ");
        } catch (Exception e) {
            log.error("check CLASSPATH for MySQL jar ", e);
        }

        try {
            conn = DriverManager.getConnection(connectionURL);
            conn.setAutoCommit(false);
            log.info("Connected to database " + connectionURL);

            // Create tables if needed
            if (!isTableExists("settings")) {
                createTables();
            }
            initFromDatabase();
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
    }

    public void resetStore() throws BlockStoreException {
        Statement s;
        try {
            s = conn.createStatement();
            s.executeUpdate("DROP TABLE settings");
            s.executeUpdate("DROP TABLE blocks");
            s.close();
            createTables();
            initFromDatabase();
            startCommitter();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void createTables() throws SQLException, BlockStoreException {
        Statement s = conn.createStatement();
        log.debug("MySQLBlockStore : CREATE blocks table");
        s.executeUpdate(CREATE_BLOCKS_TABLE);

        log.debug("MySQLBlockStore : CREATE settings table");
        s.executeUpdate(CREATE_SETTINGS_TABLE);

        s.executeUpdate("INSERT INTO settings(name, value) VALUES('chainhead', NULL)");
        createNewStore(params);
    }

    private void initFromDatabase() throws SQLException, BlockStoreException {
        Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery("SELECT value FROM settings WHERE name = 'chainhead'");
        if (!rs.next()) {
            throw new BlockStoreException("corrupt MySQL block store - no chain head pointer");
        }
        Sha256Hash hash = new Sha256Hash(rs.getBytes(1));
        this.chainHeadBlock = get(hash);
        if (this.chainHeadBlock == null)
        {
            throw new BlockStoreException("corrupt MySQL block store - head block not found");
        }
        this.chainHeadHash = hash;
    }

    private void createNewStore(NetworkParameters params) throws BlockStoreException {
        try {
            // Set up the genesis block. When we start out fresh, it is by
            // definition the top of the chain.
            Block genesis = params.genesisBlock.cloneAsHeader();
            StoredBlock storedGenesis = new StoredBlock(genesis,
                    genesis.getWork(), 0);
            this.chainHeadBlock = storedGenesis;
            this.chainHeadHash = storedGenesis.getHeader().getHash();
            setChainHead(storedGenesis);
            put(storedGenesis);
        } catch (VerificationException e1) {
            throw new RuntimeException(e1); // Cannot happen.
        }
    }

    private boolean isTableExists(String table) throws SQLException {
        Statement s = conn.createStatement();
        try {
            ResultSet results = s.executeQuery("SELECT * FROM " + table + " WHERE 1 = 2");
            results.close();
            return true;
        } catch (SQLException ex) {
            return false;
        } finally {
            s.close();
        }
    }

    public void put(StoredBlock stored) throws BlockStoreException {
        try {
            PreparedStatement s =
                    conn.prepareStatement("INSERT INTO blocks(hash, chainWork, height, header)"
                            + " VALUES(?, ?, ?, ?)");
            s.setBytes(1, stored.getHeader().getHash().getBytes());
            s.setBytes(2, stored.getChainWork().toByteArray());
            s.setLong(3, stored.getHeight());
            s.setBytes(4, stored.getHeader().unsafeLitecoinSerialize());
            s.executeUpdate();
            s.close();
            startCommitter();
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
    }

    public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        // Optimize for chain head
        if (chainHeadHash != null && chainHeadHash.equals(hash))
            return chainHeadBlock;
        try {
            PreparedStatement s = conn
                    .prepareStatement("SELECT chainWork, height, header FROM blocks WHERE hash = ?");
            s.setBytes(1, hash.getBytes());
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.

            BigInteger chainWork = new BigInteger(results.getBytes(1));
            int height = results.getInt(2);
            Block b = new Block(params, results.getBytes(3));
            StoredBlock stored;
            //b.verifyHeader();
            stored = new StoredBlock(b, chainWork, height);
            return stored;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } catch (ProtocolException e) {
            // Corrupted database.
            throw new BlockStoreException(e);

        } /*catch (VerificationException e) {
            // Should not be able to happen unless the database contains bad
            // blocks.
            throw new BlockStoreException(e);
        }            */
    }

    public StoredBlock getChainHead() throws BlockStoreException {
        return chainHeadBlock;
    }

    public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        Sha256Hash hash = chainHead.getHeader().getHash();
        this.chainHeadHash = hash;
        this.chainHeadBlock = chainHead;
        try {
            PreparedStatement s = conn
                    .prepareStatement("UPDATE settings SET value = ? WHERE name = ?");
            s.setString(2, CHAIN_HEAD_SETTING);
            s.setBytes(1, hash.getBytes());
            s.executeUpdate();
            s.close();
            startCommitter();
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
    }

    public void dump() throws SQLException {
        Statement s = conn.createStatement();
        System.out.println("settings");
        ResultSet rs = s.executeQuery("SELECT name, value FROM settings");
        while (rs.next()) {
            System.out.print(rs.getString(1));
            System.out.print(" ");
            System.out.println(Utils.bytesToHexString(rs.getBytes(2)));
        }
        rs.close();
        System.out.println("blocks");
        rs = s.executeQuery("SELECT hash, chainWork, height, header FROM blocks");
        while (rs.next()) {
            System.out.print(Utils.bytesToHexString(rs.getBytes(1)));
            System.out.print(" ");
            System.out.print(Utils.bytesToHexString(rs.getBytes(2)));
            System.out.print(" ");
            System.out.print(rs.getInt(3));
            System.out.print(" ");
            //System.out.print(Utils.bytesToHexString(rs.getBytes(4)));
            System.out.println();
        }
        rs.close();
        System.out.println("end");
        s.close();
    }

    protected synchronized void startCommitter() {
        if (committerThread != null)
            return;

        // A thread that is guaranteed to try a commit as long as
        // committerThread is not null
        Runnable committer = new Runnable() {
            public void run() {
                try {
                    log.info("commit scheduled");
                    Thread.sleep(COMMIT_INTERVAL);
                } catch (InterruptedException ex) {
                    // ignore
                }
                synchronized (MySQLBlockStore.this) {
                    try {
                        if (conn != null) {
                            commit();
                            log.info("commit success");

                        }
                        else {
                            log.info("committer noticed that we are shutting down");
                        }
                    }
                    catch (BlockStoreException e) {
                        log.warn("commit failed");
                        // ignore
                    }
                    finally {
                        committerThread = null;
                    }
                }
            }
        };

        committerThread = new Thread(committer, "MySQLBlockStore committer");
        committerThread.start();
    }
}