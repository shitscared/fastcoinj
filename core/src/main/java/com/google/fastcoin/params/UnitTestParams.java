/*
 * Copyright 2013 Google Inc.
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

package com.google.fastcoin.params;

import com.google.fastcoin.core.Block;
import com.google.fastcoin.core.NetworkParameters;
import com.google.fastcoin.core.Utils;

import java.math.BigInteger;

/**
 * Network parameters used by the fastcoinj unit tests (and potentially your own). This lets you solve a block using
 * {@link com.google.fastcoin.core.Block#solve()} by setting difficulty to the easiest possible.
 */
public class UnitTestParams extends NetworkParameters {
    public UnitTestParams() {
        super();
        id = ID_UNITTESTNET;
        packetMagic = 0xFBC0B6DB;
        addressHeader = 73;
        dumpedPrivateKeyHeader = addressHeader + 128; //This is always addressHeader + 128
        acceptableAddressCodes = new int[] { addressHeader };
        proofOfWorkLimit = new BigInteger("00ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
        genesisBlock.setDifficultyTarget(Block.EASIEST_DIFFICULTY_TARGET);
        genesisBlock.setTime(System.currentTimeMillis() / 1000);
        genesisBlock.solve();
        port = 11081;
        interval = 10;
        interval_v1 = 10;
        targetTimespan = 200000000;  // 6 years. Just a very big number.
        spendableCoinbaseDepth = 5;
        subsidyDecreaseBlockCount = 100;
        dnsSeeds = null;
    }

    private static UnitTestParams instance;
    public static synchronized UnitTestParams get() {
        if (instance == null) {
            instance = new UnitTestParams();
        }
        return instance;
    }
}
