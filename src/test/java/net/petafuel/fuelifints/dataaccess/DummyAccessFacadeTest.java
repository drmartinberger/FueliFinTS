package net.petafuel.fuelifints.dataaccess;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.*;

public class DummyAccessFacadeTest {

    private DummyAccessFacade dummyAccessFacade;

    @Before
    public void setup() {
        dummyAccessFacade = new DummyAccessFacade(null);
    }

    @After
    public void tearDown() {
        dummyAccessFacade.clearDB();
        dummyAccessFacade.close();
    }

    @AfterClass
    public static void afterClass() {

        File file = new File("dummyfacade-test.db");
        file.delete();
        file.deleteOnExit();
    }

    @Test
    public void testJdbcSqliteConnection() {
        DummyAccessFacade dummyAccessFacade = new DummyAccessFacade(null);
    }

    @Test
    public void testHasKeyPair() {
        assertFalse(dummyAccessFacade.hasKeyPair(SecurityMethod.RDH_10, "V"));
    }

    @Test
    public void testInsertKeyPair() {
        dummyAccessFacade.updateKeyPair(SecurityMethod.RDH_10, "privatekey", "publickey", "V", null, null);
        assertTrue(dummyAccessFacade.hasKeyPair(SecurityMethod.RDH_10, "V"));
    }

    @Test
    public void testGetKeys() {
        dummyAccessFacade.updateKeyPair(SecurityMethod.RDH_10, "privatekey", "publickey", "V", null, null);
        String privateKey = dummyAccessFacade.getPrivateKey(SecurityMethod.RDH_10, "V");
        String publicKey = dummyAccessFacade.getPublicKey(SecurityMethod.RDH_10, "V");
        assertNotNull(privateKey);
        assertNotNull(publicKey);
    }

    @Test
    public void testGetKeysNotInserted() {
        String privateKey = dummyAccessFacade.getPrivateKey(SecurityMethod.RDH_10, "S");
        String publicKey = dummyAccessFacade.getPublicKey(SecurityMethod.RDH_10, "S");
        assertNull(privateKey);
        assertNull(publicKey);
    }

    @Test
    public void testUpdateKeyPair() {
        dummyAccessFacade.updateKeyPair(SecurityMethod.RDH_10, "privatekey2", "publickey2", "V", null, null);
        assertTrue(dummyAccessFacade.hasKeyPair(SecurityMethod.RDH_10, "V"));
    }

    @Test
    public void testAddUserKey() {
        dummyAccessFacade.addUserPublicKey("AUserId", null, SecurityMethod.RDH_10, "uPubKey", "V", 1, 10, null, null);
        String chipherUserPublicKey = dummyAccessFacade.getUserPublicKey("AUserId", null, SecurityMethod.RDH_10, "V", 1, 10);
        assertNotNull(chipherUserPublicKey);
    }
}
