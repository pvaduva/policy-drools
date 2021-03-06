/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.core.lock;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi.OperResult;

public class PolicyResourceLockManagerTest {

    private static final int MAX_AGE_SEC = 4 * 60;

    private static final String NULL_RESOURCE_ID = "null resourceId";
    private static final String NULL_OWNER = "null owner";

    private static final String RESOURCE_A = "resource.a";
    private static final String RESOURCE_B = "resource.b";
    private static final String RESOURCE_C = "resource.c";

    private static final String OWNER1 = "owner.one";
    private static final String OWNER2 = "owner.two";
    private static final String OWNER3 = "owner.three";

    private PolicyResourceLockFeatureApi impl1;
    private PolicyResourceLockFeatureApi impl2;
    private List<PolicyResourceLockFeatureApi> implList;

    private PolicyResourceLockManager mgr;

    /**
     * Set up.
     */
    @Before
    public void setUp() {
        impl1 = mock(PolicyResourceLockFeatureApi.class);
        impl2 = mock(PolicyResourceLockFeatureApi.class);

        initImplementer(impl1);
        initImplementer(impl2);

        // list of feature API implementers
        implList = new LinkedList<>(Arrays.asList(impl1, impl2));

        mgr = new PolicyResourceLockManager() {
            @Override
            protected List<PolicyResourceLockFeatureApi> getImplementers() {
                return implList;
            }
        };
    }

    /**
     * Initializes an implementer so it always returns {@code null}.
     * 
     * @param impl implementer
     */
    private void initImplementer(PolicyResourceLockFeatureApi impl) {
        when(impl.beforeLock(anyString(), anyString(), anyInt())).thenReturn(OperResult.OPER_UNHANDLED);
        when(impl.beforeRefresh(anyString(), anyString(), anyInt())).thenReturn(OperResult.OPER_UNHANDLED);
        when(impl.beforeUnlock(anyString(), anyString())).thenReturn(OperResult.OPER_UNHANDLED);
        when(impl.beforeIsLocked(anyString())).thenReturn(OperResult.OPER_UNHANDLED);
        when(impl.beforeIsLockedBy(anyString(), anyString())).thenReturn(OperResult.OPER_UNHANDLED);
    }

    @Test
    public void testLock() throws Exception {
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).beforeLock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl2).beforeLock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl1).afterLock(RESOURCE_A, OWNER1, true);
        verify(impl2).afterLock(RESOURCE_A, OWNER1, true);

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        // null callback - not locked yet
        assertTrue(mgr.lock(RESOURCE_C, OWNER3, MAX_AGE_SEC));

        // null callback - already locked
        assertFalse(mgr.lock(RESOURCE_A, OWNER3, MAX_AGE_SEC));
    }

    @Test
    public void testLock_ArgEx() {
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.lock(null, OWNER1, MAX_AGE_SEC))
                        .withMessage(NULL_RESOURCE_ID);

        assertThatIllegalArgumentException().isThrownBy(() -> mgr.lock(RESOURCE_A, null, MAX_AGE_SEC))
                        .withMessage(NULL_OWNER);

        // this should not throw an exception
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
    }

    @Test
    public void testLock_Acquired_BeforeIntercepted() {
        // have impl1 intercept
        when(impl1.beforeLock(RESOURCE_A, OWNER1, MAX_AGE_SEC)).thenReturn(OperResult.OPER_ACCEPTED);

        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).beforeLock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl2, never()).beforeLock(anyString(), anyString(), anyInt());

        verify(impl1, never()).afterLock(anyString(), anyString(), anyBoolean());
        verify(impl2, never()).afterLock(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testLock_Denied_BeforeIntercepted() {
        // have impl1 intercept
        when(impl1.beforeLock(RESOURCE_A, OWNER1, MAX_AGE_SEC)).thenReturn(OperResult.OPER_DENIED);

        assertFalse(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).beforeLock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl2, never()).beforeLock(anyString(), anyString(), anyInt());

        verify(impl1, never()).afterLock(anyString(), anyString(), anyBoolean());
        verify(impl2, never()).afterLock(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testLock_Acquired_AfterIntercepted() throws Exception {

        // impl1 intercepts during afterLock()
        when(impl1.afterLock(RESOURCE_A, OWNER1, true)).thenReturn(true);

        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // impl1 sees it, but impl2 does not
        verify(impl1).afterLock(RESOURCE_A, OWNER1, true);
        verify(impl2, never()).afterLock(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testLock_Acquired() throws Exception {
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).afterLock(RESOURCE_A, OWNER1, true);
        verify(impl2).afterLock(RESOURCE_A, OWNER1, true);
    }

    @Test
    public void testLock_Denied_AfterIntercepted() throws Exception {

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // impl1 intercepts during afterLock()
        when(impl1.afterLock(RESOURCE_A, OWNER2, false)).thenReturn(true);

        // owner2 tries to lock
        assertFalse(mgr.lock(RESOURCE_A, OWNER2, MAX_AGE_SEC));

        // impl1 sees it, but impl2 does not
        verify(impl1).afterLock(RESOURCE_A, OWNER2, false);
        verify(impl2, never()).afterLock(RESOURCE_A, OWNER2, false);
    }

    @Test
    public void testLock_Denied() {

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // owner2 tries to lock
        mgr.lock(RESOURCE_A, OWNER2, MAX_AGE_SEC);

        verify(impl1).afterLock(RESOURCE_A, OWNER2, false);
        verify(impl2).afterLock(RESOURCE_A, OWNER2, false);
    }

    @Test
    public void testRefresh() throws Exception {
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));
        assertTrue(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).beforeRefresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl2).beforeRefresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl1).afterRefresh(RESOURCE_A, OWNER1, true);
        verify(impl2).afterRefresh(RESOURCE_A, OWNER1, true);

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        // different owner and resource
        assertFalse(mgr.refresh(RESOURCE_C, OWNER3, MAX_AGE_SEC));

        // different owner
        assertFalse(mgr.refresh(RESOURCE_A, OWNER3, MAX_AGE_SEC));
    }

    @Test
    public void testRefresh_ArgEx() {
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.refresh(null, OWNER1, MAX_AGE_SEC))
                        .withMessage(NULL_RESOURCE_ID);

        assertThatIllegalArgumentException().isThrownBy(() -> mgr.refresh(RESOURCE_A, null, MAX_AGE_SEC))
                        .withMessage(NULL_OWNER);

        // this should not throw an exception
        mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);
    }

    @Test
    public void testRefresh_Acquired_BeforeIntercepted() {
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // have impl1 intercept
        when(impl1.beforeRefresh(RESOURCE_A, OWNER1, MAX_AGE_SEC)).thenReturn(OperResult.OPER_ACCEPTED);

        assertTrue(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).beforeRefresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl2, never()).beforeRefresh(anyString(), anyString(), anyInt());

        verify(impl1, never()).afterRefresh(anyString(), anyString(), anyBoolean());
        verify(impl2, never()).afterRefresh(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testRefresh_Denied_BeforeIntercepted() {
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // have impl1 intercept
        when(impl1.beforeRefresh(RESOURCE_A, OWNER1, MAX_AGE_SEC)).thenReturn(OperResult.OPER_DENIED);

        assertFalse(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).beforeRefresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        verify(impl2, never()).beforeRefresh(anyString(), anyString(), anyInt());

        verify(impl1, never()).afterRefresh(anyString(), anyString(), anyBoolean());
        verify(impl2, never()).afterRefresh(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testRefresh_Acquired_AfterIntercepted() throws Exception {
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // impl1 intercepts during afterRefresh()
        when(impl1.afterRefresh(RESOURCE_A, OWNER1, true)).thenReturn(true);

        assertTrue(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // impl1 sees it, but impl2 does not
        verify(impl1).afterRefresh(RESOURCE_A, OWNER1, true);
        verify(impl2, never()).afterRefresh(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testRefresh_Acquired() throws Exception {
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));
        
        assertTrue(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        verify(impl1).afterRefresh(RESOURCE_A, OWNER1, true);
        verify(impl2).afterRefresh(RESOURCE_A, OWNER1, true);
    }

    @Test
    public void testRefresh_Denied_AfterIntercepted() throws Exception {

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // impl1 intercepts during afterRefresh()
        when(impl1.afterRefresh(RESOURCE_A, OWNER2, false)).thenReturn(true);

        // owner2 tries to lock
        assertFalse(mgr.refresh(RESOURCE_A, OWNER2, MAX_AGE_SEC));

        // impl1 sees it, but impl2 does not
        verify(impl1).afterRefresh(RESOURCE_A, OWNER2, false);
        verify(impl2, never()).afterRefresh(RESOURCE_A, OWNER2, false);
    }

    @Test
    public void testRefresh_Denied() {

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // owner2 tries to lock
        mgr.refresh(RESOURCE_A, OWNER2, MAX_AGE_SEC);

        verify(impl1).afterRefresh(RESOURCE_A, OWNER2, false);
        verify(impl2).afterRefresh(RESOURCE_A, OWNER2, false);
    }

    @Test
    public void testUnlock() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        mgr.lock(RESOURCE_B, OWNER1, MAX_AGE_SEC);

        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));

        verify(impl1).beforeUnlock(RESOURCE_A, OWNER1);
        verify(impl2).beforeUnlock(RESOURCE_A, OWNER1);

        verify(impl1).afterUnlock(RESOURCE_A, OWNER1, true);
        verify(impl2).afterUnlock(RESOURCE_A, OWNER1, true);
    }

    @Test
    public void testUnlock_ArgEx() {
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.unlock(null, OWNER1)).withMessage(NULL_RESOURCE_ID);

        assertThatIllegalArgumentException().isThrownBy(() -> mgr.unlock(RESOURCE_A, null)).withMessage(NULL_OWNER);
    }

    @Test
    public void testUnlock_BeforeInterceptedTrue() {

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // have impl1 intercept
        when(impl1.beforeUnlock(RESOURCE_A, OWNER1)).thenReturn(OperResult.OPER_ACCEPTED);

        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));

        verify(impl1).beforeUnlock(RESOURCE_A, OWNER1);
        verify(impl2, never()).beforeUnlock(anyString(), anyString());

        verify(impl1, never()).afterUnlock(anyString(), anyString(), anyBoolean());
        verify(impl2, never()).afterUnlock(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testUnlock_BeforeInterceptedFalse() {

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // have impl1 intercept
        when(impl1.beforeUnlock(RESOURCE_A, OWNER1)).thenReturn(OperResult.OPER_DENIED);

        assertFalse(mgr.unlock(RESOURCE_A, OWNER1));

        verify(impl1).beforeUnlock(RESOURCE_A, OWNER1);
        verify(impl2, never()).beforeUnlock(anyString(), anyString());

        verify(impl1, never()).afterUnlock(anyString(), anyString(), anyBoolean());
        verify(impl2, never()).afterUnlock(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void testUnlock_Unlocked() {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));

        verify(impl1).beforeUnlock(RESOURCE_A, OWNER1);
        verify(impl2).beforeUnlock(RESOURCE_A, OWNER1);

        verify(impl1).afterUnlock(RESOURCE_A, OWNER1, true);
        verify(impl2).afterUnlock(RESOURCE_A, OWNER1, true);
    }

    @Test
    public void testUnlock_Unlocked_AfterIntercepted() {
        // have impl1 intercept
        when(impl1.afterUnlock(RESOURCE_A, OWNER1, true)).thenReturn(true);

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));

        verify(impl1).beforeUnlock(RESOURCE_A, OWNER1);
        verify(impl2).beforeUnlock(RESOURCE_A, OWNER1);

        verify(impl1).afterUnlock(RESOURCE_A, OWNER1, true);
        verify(impl2, never()).afterUnlock(RESOURCE_A, OWNER1, true);
    }

    @Test
    public void testUnlock_NotUnlocked() {
        assertFalse(mgr.unlock(RESOURCE_A, OWNER1));

        verify(impl1).beforeUnlock(RESOURCE_A, OWNER1);
        verify(impl2).beforeUnlock(RESOURCE_A, OWNER1);

        verify(impl1).afterUnlock(RESOURCE_A, OWNER1, false);
        verify(impl2).afterUnlock(RESOURCE_A, OWNER1, false);
    }

    @Test
    public void testUnlock_NotUnlocked_AfterIntercepted() {
        // have impl1 intercept
        when(impl1.afterUnlock(RESOURCE_A, OWNER1, false)).thenReturn(true);

        assertFalse(mgr.unlock(RESOURCE_A, OWNER1));

        verify(impl1).beforeUnlock(RESOURCE_A, OWNER1);
        verify(impl2).beforeUnlock(RESOURCE_A, OWNER1);

        verify(impl1).afterUnlock(RESOURCE_A, OWNER1, false);
        verify(impl2, never()).afterUnlock(RESOURCE_A, OWNER1, false);
    }

    @Test
    public void testIsLocked_True() {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        assertTrue(mgr.isLocked(RESOURCE_A));

        verify(impl1).beforeIsLocked(RESOURCE_A);
        verify(impl2).beforeIsLocked(RESOURCE_A);
    }

    @Test
    public void testIsLocked_False() {
        assertFalse(mgr.isLocked(RESOURCE_A));

        verify(impl1).beforeIsLocked(RESOURCE_A);
        verify(impl2).beforeIsLocked(RESOURCE_A);
    }

    @Test
    public void testIsLocked_ArgEx() {
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.isLocked(null)).withMessage(NULL_RESOURCE_ID);
    }

    @Test
    public void testIsLocked_BeforeIntercepted_True() {

        // have impl1 intercept
        when(impl1.beforeIsLocked(RESOURCE_A)).thenReturn(OperResult.OPER_ACCEPTED);;

        assertTrue(mgr.isLocked(RESOURCE_A));

        verify(impl1).beforeIsLocked(RESOURCE_A);
        verify(impl2, never()).beforeIsLocked(RESOURCE_A);
    }

    @Test
    public void testIsLocked_BeforeIntercepted_False() {

        // lock it so we can verify that impl1 overrides the superclass isLocker()
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // have impl1 intercept
        when(impl1.beforeIsLocked(RESOURCE_A)).thenReturn(OperResult.OPER_DENIED);

        assertFalse(mgr.isLocked(RESOURCE_A));

        verify(impl1).beforeIsLocked(RESOURCE_A);
        verify(impl2, never()).beforeIsLocked(RESOURCE_A);
    }

    @Test
    public void testIsLockedBy_True() {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));

        verify(impl1).beforeIsLockedBy(RESOURCE_A, OWNER1);
        verify(impl2).beforeIsLockedBy(RESOURCE_A, OWNER1);
    }

    @Test
    public void testIsLockedBy_False() {
        // different owner
        mgr.lock(RESOURCE_A, OWNER2, MAX_AGE_SEC);

        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));

        verify(impl1).beforeIsLockedBy(RESOURCE_A, OWNER1);
        verify(impl2).beforeIsLockedBy(RESOURCE_A, OWNER1);
    }

    @Test
    public void testIsLockedBy_ArgEx() {
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.isLockedBy(null, OWNER1))
                        .withMessage(NULL_RESOURCE_ID);

        assertThatIllegalArgumentException().isThrownBy(() -> mgr.isLockedBy(RESOURCE_A, null)).withMessage(NULL_OWNER);
    }

    @Test
    public void testIsLockedBy_BeforeIntercepted_True() {

        // have impl1 intercept
        when(impl1.beforeIsLockedBy(RESOURCE_A, OWNER1)).thenReturn(OperResult.OPER_ACCEPTED);;

        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));

        verify(impl1).beforeIsLockedBy(RESOURCE_A, OWNER1);
        verify(impl2, never()).beforeIsLockedBy(RESOURCE_A, OWNER1);
    }

    @Test
    public void testIsLockedBy_BeforeIntercepted_False() {

        // lock it so we can verify that impl1 overrides the superclass isLocker()
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // have impl1 intercept
        when(impl1.beforeIsLockedBy(RESOURCE_A, OWNER1)).thenReturn(OperResult.OPER_DENIED);

        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));

        verify(impl1).beforeIsLockedBy(RESOURCE_A, OWNER1);
        verify(impl2, never()).beforeIsLockedBy(RESOURCE_A, OWNER1);
    }

    @Test
    public void testGetInstance() {
        PolicyResourceLockManager inst = PolicyResourceLockManager.getInstance();
        assertNotNull(inst);

        // should return the same instance each time
        assertEquals(inst, PolicyResourceLockManager.getInstance());
        assertEquals(inst, PolicyResourceLockManager.getInstance());
    }

    @Test
    public void testDoIntercept_Empty() {
        // clear the implementer list
        implList.clear();

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertFalse(mgr.isLocked(RESOURCE_B));

        verify(impl1, never()).beforeIsLocked(anyString());
    }

    @Test
    public void testDoIntercept_Impl1() {
        when(impl1.beforeIsLocked(RESOURCE_A)).thenReturn(OperResult.OPER_ACCEPTED);;

        assertTrue(mgr.isLocked(RESOURCE_A));

        verify(impl1).beforeIsLocked(RESOURCE_A);
        verify(impl2, never()).beforeIsLocked(anyString());
    }

    @Test
    public void testDoIntercept_Impl2() {
        when(impl2.beforeIsLocked(RESOURCE_A)).thenReturn(OperResult.OPER_ACCEPTED);;

        assertTrue(mgr.isLocked(RESOURCE_A));

        verify(impl1).beforeIsLocked(RESOURCE_A);
        verify(impl2).beforeIsLocked(RESOURCE_A);
    }

    @Test
    public void testDoIntercept_Ex() {
        doThrow(new RuntimeException("expected exception")).when(impl1).beforeIsLocked(RESOURCE_A);

        assertFalse(mgr.isLocked(RESOURCE_A));

        verify(impl1).beforeIsLocked(RESOURCE_A);
        verify(impl2).beforeIsLocked(RESOURCE_A);
    }
}
