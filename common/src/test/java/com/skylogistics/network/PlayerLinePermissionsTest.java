package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerLinePermissionsTest {
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID guest = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID stranger = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test void oldWorldDefaultsToPrivateAndOwnerKeepsAccess() {
        var permissions = PlayerLinePermissions.load("");
        assertFalse(permissions.isPublic(owner));
        assertTrue(permissions.allows(owner, owner));
        assertFalse(permissions.allows(owner, guest));
        assertFalse(permissions.allows(null, guest));
        assertFalse(permissions.allows(owner, null));
    }

    @Test void grantIsDirectionalAndRevocationTakesEffectImmediately() {
        var permissions = new PlayerLinePermissions();
        permissions.setGranted(owner, guest, true);
        assertTrue(permissions.allows(owner, guest));
        assertFalse(permissions.allows(guest, owner));
        assertFalse(permissions.allows(owner, stranger));
        permissions.setGranted(owner, guest, false);
        assertFalse(permissions.allows(owner, guest));
        assertTrue(permissions.grantedPlayers(owner).isEmpty());
    }

    @Test void publicTogglePreservesExplicitGrantsAndDoesNotExposeOtherOwners() {
        var permissions = new PlayerLinePermissions();
        permissions.setGranted(owner, guest, true);
        permissions.setPublic(owner, true);
        assertTrue(permissions.allows(owner, stranger));
        assertFalse(permissions.allows(guest, stranger));
        permissions.setPublic(owner, false);
        assertFalse(permissions.allows(owner, stranger));
        assertTrue(permissions.allows(owner, guest));
    }

    @Test void restartRetainsOfflinePlayersAndUuidAuthorizationAcrossNameChanges() {
        var permissions = new PlayerLinePermissions();
        permissions.remember(guest, "BeforeRename");
        permissions.setGranted(owner, guest, true);
        permissions.setPublic(stranger, true);
        var restored = PlayerLinePermissions.load(permissions.save());
        assertEquals(Set.of(guest), restored.grantedPlayers(owner));
        assertEquals("BeforeRename", restored.name(guest));
        assertTrue(restored.isPublic(stranger));
        restored.remember(guest, "AfterRename");
        assertTrue(restored.allows(owner, guest));
        assertEquals("AfterRename", PlayerLinePermissions.load(restored.save()).name(guest));
        restored.setGranted(owner, owner, true);
        assertEquals(Set.of(guest), restored.grantedPlayers(owner));
    }
}
