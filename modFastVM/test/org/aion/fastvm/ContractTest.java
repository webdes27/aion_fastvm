package org.aion.fastvm;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.types.a0.AionInternalTx;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.ExecutionResult.Code;
import org.aion.vm.TransactionResult;
import org.aion.vm.types.DataWord;
import org.aion.vm.types.Log;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContractTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = Address.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockNumber = 1;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private DataWord blockDifficulty = new DataWord(0x100000000L);

    private DataWord nrgPrice;
    private long nrgLimit;
    private DataWord callValue;
    private byte[] callData;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    private TransactionResult txResult;

    public ContractTest() throws CloneNotSupportedException {
    }

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 20000;
        callValue = DataWord.ZERO;
        callData = new byte[0];
        txResult = new TransactionResult();
    }

    @Test
    public void testByteArrayMap() throws IOException {
        byte[] contract = ContractUtils.getContractBody("ByteArrayMap.sol", "ByteArrayMap");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        callData = Hex.decode("26121ff0");
        nrgLimit = 1_000_000L;

        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());

        callData = Hex.decode("e2179b8e");
        nrgLimit = 1_000_000L;

        ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue, callData, depth,
                kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit, blockDifficulty, txResult);
        vm = new FastVM();
        result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());

        assertEquals(
                "000000000000000000000000000000100000000000000000000000000000040061000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000062",
                Hex.toHexString(result.getOutput()));
    }

    @Test
    public void testFibonacci() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Fibonacci.sol", "Fibonacci");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        callData = ByteUtil.merge(Hex.decode("ff40565e"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("231e93d4"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
        ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue, callData, depth,
                kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit, blockDifficulty, txResult);
        vm = new FastVM();
        result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("1dae8972"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
        ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue, callData, depth,
                kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit, blockDifficulty, txResult);
        vm = new FastVM();
        result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), new DataWord(6L).getData());
        nrgLimit = 100_000L;
        ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue, callData, depth,
                kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit, blockDifficulty, txResult);
        vm = new FastVM();
        result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals(new DataWord(8L).toString(), Hex.toHexString(result.getOutput()));

        callData = ByteUtil.merge(Hex.decode("9d4cd86c"), new DataWord(1024L).getData());
        nrgLimit = 100_000L;
        ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue, callData, depth,
                kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit, blockDifficulty, txResult);
        vm = new FastVM();
        result = vm.run(contract, ctx, repo);
        System.out.println(result);
        assertEquals(Code.REVERT, result.getCode());
        assertTrue(result.getNrgLeft() > 0);
    }

    @Test
    public void testRecursive1() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        int n = 10;
        callData = ByteUtil.merge(Hex.decode("2d7df21a"), address.toBytes(),
                new DataWord(n).getData());
        nrgLimit = 100_000L;
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);

        // verify result
        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals(new DataWord(n).toString(), Hex.toHexString(result.getOutput()));

        // verify internal transactions
        List<AionInternalTx> txs = ctx.result().getInternalTransactions();
        assertEquals(n - 1, txs.size());
        for (AionInternalTx tx : txs) {
            System.out.println(tx);
        }

        // verify logs
        List<Log> logs = ctx.result().getLogs();
        assertEquals(n, logs.size());
        for (Log log : logs) {
            System.out.println(log);
        }
    }

    @Test
    public void testRecursive2() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        int n = 128;
        callData = ByteUtil.merge(Hex.decode("2d7df21a"), address.toBytes(),
                new DataWord(n).getData());
        nrgLimit = 10_000_000L;
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);

        // verify result
        assertEquals(Code.SUCCESS, result.getCode());
        assertEquals(new DataWord(n).toString(), Hex.toHexString(result.getOutput()));

        // verify internal transactions
        List<AionInternalTx> txs = ctx.result().getInternalTransactions();
        assertEquals(n - 1, txs.size());

        // verify logs
        List<Log> logs = ctx.result().getLogs();
        assertEquals(n, logs.size());
    }

    @Test
    public void testRecursive3() throws IOException {
        byte[] contract = ContractUtils.getContractBody("Recursive.sol", "Recursive");

        DummyRepository repo = new DummyRepository();
        repo.addContract(address, contract);

        int n = 1000;
        callData = ByteUtil.merge(Hex.decode("2d7df21a"), address.toBytes(),
                new DataWord(n).getData());
        nrgLimit = 10_000_000L;
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();
        ExecutionResult result = vm.run(contract, ctx, repo);
        System.out.println(result);

        // verify result
        assertEquals(Code.REVERT, result.getCode());
    }
}
