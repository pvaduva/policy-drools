/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

public class TestUtils {

    /**
     * Invokes a function that is expected to throw an exception.
     * 
     * @param clazz class of exception that is expected
     * @param func
     * @return
     */
    public static <T> T expectException(Class<T> clazz, VoidFunction func) {
        try {
            func.apply(null);
            throw new AssertionError("missing exception");

        } catch (Exception e) {
            try {
                return clazz.cast(e);

            } catch (ClassCastException e2) {
                throw new AssertionError("incorrect exception type", e2);
            }
        }
    }

    /**
     * Void function that may throw an exception.
     */
    @FunctionalInterface
    public static interface VoidFunction {

        /**
         * 
         * @param arg always {@code null}
         */
        public void apply(Void arg) throws Exception;
    }
}
