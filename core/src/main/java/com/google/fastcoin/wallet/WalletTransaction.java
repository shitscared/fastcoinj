/**
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.fastcoin.wallet;

import com.google.fastcoin.core.Transaction;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores data about a transaction that is only relevant to the {@link com.google.fastcoin.core.Wallet} class.
 */
public class WalletTransaction {
    public enum Pool {
        UNSPENT(4), // unspent in best chain
        SPENT(5), // spent in best chain
        INACTIVE(2), // in alt chain
        DEAD(10), // double-spend in alt chain
        PENDING(16), // a pending tx we would like to go into the best chain
        PENDING_INACTIVE(18), // a pending tx in alt but not in best yet
        ALL(-1);

        private int value;
        Pool(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Pool valueOf(int value) {
            switch (value) {
                case 4: return UNSPENT;
                case 5: return SPENT;
                case 2: return INACTIVE;
                case 10: return DEAD;
                case 16: return PENDING;
                case 18: return PENDING_INACTIVE;
                default: return null;
            }
        }
    }
    private final Transaction transaction;
    private final Pool pool;
    
    public WalletTransaction(Pool pool, Transaction transaction) {
        this.pool = checkNotNull(pool);
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }
    
    public Pool getPool() {
        return pool;
    }
}

