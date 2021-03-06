package org.aion.fastvm;

import static org.aion.fastvm.Instruction.ADD;
import static org.aion.fastvm.Instruction.ADDMOD;
import static org.aion.fastvm.Instruction.ADDRESS;
import static org.aion.fastvm.Instruction.AND;
import static org.aion.fastvm.Instruction.BYTE;
import static org.aion.fastvm.Instruction.CALLDATALOAD;
import static org.aion.fastvm.Instruction.CALLDATASIZE;
import static org.aion.fastvm.Instruction.CALLER;
import static org.aion.fastvm.Instruction.CALLVALUE;
import static org.aion.fastvm.Instruction.CODESIZE;
import static org.aion.fastvm.Instruction.COINBASE;
import static org.aion.fastvm.Instruction.DIFFICULTY;
import static org.aion.fastvm.Instruction.DIV;
import static org.aion.fastvm.Instruction.DUP1;
import static org.aion.fastvm.Instruction.EQ;
import static org.aion.fastvm.Instruction.EXP;
import static org.aion.fastvm.Instruction.GAS;
import static org.aion.fastvm.Instruction.GASLIMIT;
import static org.aion.fastvm.Instruction.GASPRICE;
import static org.aion.fastvm.Instruction.GT;
import static org.aion.fastvm.Instruction.ISZERO;
import static org.aion.fastvm.Instruction.JUMPI;
import static org.aion.fastvm.Instruction.LT;
import static org.aion.fastvm.Instruction.MLOAD;
import static org.aion.fastvm.Instruction.MOD;
import static org.aion.fastvm.Instruction.MSIZE;
import static org.aion.fastvm.Instruction.MSTORE;
import static org.aion.fastvm.Instruction.MSTORE8;
import static org.aion.fastvm.Instruction.MUL;
import static org.aion.fastvm.Instruction.MULMOD;
import static org.aion.fastvm.Instruction.NOT;
import static org.aion.fastvm.Instruction.NUMBER;
import static org.aion.fastvm.Instruction.OR;
import static org.aion.fastvm.Instruction.ORIGIN;
import static org.aion.fastvm.Instruction.PC;
import static org.aion.fastvm.Instruction.POP;
import static org.aion.fastvm.Instruction.PUSH1;
import static org.aion.fastvm.Instruction.SDIV;
import static org.aion.fastvm.Instruction.SGT;
import static org.aion.fastvm.Instruction.SHA3;
import static org.aion.fastvm.Instruction.SIGNEXTEND;
import static org.aion.fastvm.Instruction.SLT;
import static org.aion.fastvm.Instruction.SMOD;
import static org.aion.fastvm.Instruction.SUB;
import static org.aion.fastvm.Instruction.SWAP1;
import static org.aion.fastvm.Instruction.TIMESTAMP;
import static org.aion.fastvm.Instruction.XOR;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import org.aion.ExternalCapabilitiesForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.ExternalStateForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.types.AionAddress;
import org.aion.fastvm.Instruction.Tier;
import org.aion.fastvm.util.HexUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NrgCostTest {
    private byte[] txHash = RandomUtils.nextBytes(32);
    private AionAddress origin = new AionAddress(RandomUtils.nextBytes(32));
    private AionAddress caller = origin;
    private AionAddress address;

    private AionAddress blockCoinbase = new AionAddress(RandomUtils.nextBytes(32));
    private long blockNumber = 1;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private FvmDataWord blockDifficulty = FvmDataWord.fromLong(0x100000000L);

    private long nrgPrice;
    private long nrgLimit;
    private BigInteger callValue;
    private byte[] callData;

    private int depth = 0;
    private TransactionKind kind = TransactionKind.CREATE;
    private int flags = 0;

    public NrgCostTest() {}

    private RepositoryForTesting repo;

    @BeforeClass
    public static void note() {
        System.out.println(
                "\nNOTE: compilation time was not counted; extra cpu time was introduced for some opcodes.");

        CapabilitiesProvider.installExternalCapabilities(new ExternalCapabilitiesForTesting());
    }

    @AfterClass
    public static void teardownClass() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void setup() {
        nrgPrice = 1;
        nrgLimit = 10_000_000L;
        callValue = BigInteger.ZERO;
        callData = new byte[0];

        address = new AionAddress(RandomUtils.nextBytes(32));
        repo = RepositoryForTesting.newRepository();

        // JVM warm up
        byte[] code = {0x00};
        ExecutionContext ctx = newExecutionContext();
        repo.createAccount(address);
        repo.saveCode(address, code);
        for (int i = 0; i < 10000; i++) {
            new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
        }
    }

    private byte[] repeat(int n, Object... codes) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int i = 0; i < n; i++) {
            for (Object o : codes) {
                buf.write(
                        o instanceof Instruction
                                ? ((Instruction) o).code()
                                : ((Integer) o).byteValue());
            }
        }

        return buf.toByteArray();
    }

    @Test
    public void test1BasePreFork() {
        /**
         * Number of repeats of the instruction. You may get different results by adjusting this
         * number. It's a tradeoff between the instruction execution and system cost. We should only
         * interpret the results relatively.
         */
        int x = 64;

        /** Number of VM invoking. */
        int y = 1000;

        /** Energy cost for this group of instructions. */
        int z = Tier.BASE.cost(); // energy cost

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the Base tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {
            ADDRESS,
            ORIGIN,
            CALLER,
            CALLVALUE,
            CALLDATASIZE,
            CODESIZE,
            GASPRICE,
            COINBASE,
            TIMESTAMP,
            NUMBER,
            DIFFICULTY,
            GASLIMIT, /* POP, */
            PC,
            MSIZE,
            GAS
        };

        for (Instruction inst : instructions) {
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                    new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }

        System.out.println();
    }

    @Test
    public void test1BasePostFork() {
        /**
         * Number of repeats of the instruction. You may get different results by adjusting this
         * number. It's a tradeoff between the instruction execution and system cost. We should only
         * interpret the results relatively.
         */
        int x = 64;

        /** Number of VM invoking. */
        int y = 1000;

        /** Energy cost for this group of instructions. */
        int z = Tier.BASE.cost(); // energy cost

        System.out.println(
            "\n========================================================================");
        System.out.println("Cost for instructions of the Base tier");
        System.out.println(
            "========================================================================");

        Instruction[] instructions = {
            ADDRESS,
            ORIGIN,
            CALLER,
            CALLVALUE,
            CALLDATASIZE,
            CODESIZE,
            GASPRICE,
            COINBASE,
            TIMESTAMP,
            NUMBER,
            DIFFICULTY,
            GASLIMIT, /* POP, */
            PC,
            MSIZE,
            GAS
        };

        for (Instruction inst : instructions) {
            byte[] code =
                repeat(
                    x,
                    PUSH1,
                    32,
                    CALLDATALOAD,
                    PUSH1,
                    16,
                    CALLDATALOAD,
                    PUSH1,
                    0,
                    CALLDATALOAD,
                    inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }

        System.out.println();
    }

    @Test
    public void test2VeryLowPreFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.VERY_LOW.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the VeryLow tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {
            ADD,
            SUB,
            NOT,
            LT,
            GT,
            SLT,
            SGT,
            EQ,
            ISZERO,
            AND,
            OR,
            XOR,
            BYTE,
            CALLDATALOAD,
            MLOAD,
            MSTORE,
            MSTORE8, /* PUSH1, */
            DUP1,
            SWAP1
        };

        for (Instruction inst : instructions) {
            callData =
                    HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                    new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test2VeryLowPostFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.VERY_LOW.cost();

        System.out.println(
            "\n========================================================================");
        System.out.println("Cost for instructions of the VeryLow tier");
        System.out.println(
            "========================================================================");

        Instruction[] instructions = {
            ADD,
            SUB,
            NOT,
            LT,
            GT,
            SLT,
            SGT,
            EQ,
            ISZERO,
            AND,
            OR,
            XOR,
            BYTE,
            CALLDATALOAD,
            MLOAD,
            MSTORE,
            MSTORE8, /* PUSH1, */
            DUP1,
            SWAP1
        };

        for (Instruction inst : instructions) {
            callData =
                HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                repeat(
                    x,
                    PUSH1,
                    32,
                    CALLDATALOAD,
                    PUSH1,
                    16,
                    CALLDATALOAD,
                    PUSH1,
                    0,
                    CALLDATALOAD,
                    inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test3LowPreFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.LOW.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the Low tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {MUL, DIV, SDIV, MOD, SMOD, SIGNEXTEND};

        for (Instruction inst : instructions) {
            callData =
                    HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                    new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test3LowPostFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.LOW.cost();

        System.out.println(
            "\n========================================================================");
        System.out.println("Cost for instructions of the Low tier");
        System.out.println(
            "========================================================================");

        Instruction[] instructions = {MUL, DIV, SDIV, MOD, SMOD, SIGNEXTEND};

        for (Instruction inst : instructions) {
            callData =
                HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                repeat(
                    x,
                    PUSH1,
                    32,
                    CALLDATALOAD,
                    PUSH1,
                    16,
                    CALLDATALOAD,
                    PUSH1,
                    0,
                    CALLDATALOAD,
                    inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test4MidPreFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.MID.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the Mid tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {
            ADDMOD, MULMOD, /* JUMP */
        };

        for (Instruction inst : instructions) {
            callData =
                    HexUtil.decode(
                            "000000000000000000000000000000010000000000000000000000000000000200000000000000000000000000000003");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                    new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test4MidPostFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.MID.cost();

        System.out.println(
            "\n========================================================================");
        System.out.println("Cost for instructions of the Mid tier");
        System.out.println(
            "========================================================================");

        Instruction[] instructions = {
            ADDMOD, MULMOD, /* JUMP */
        };

        for (Instruction inst : instructions) {
            callData =
                HexUtil.decode(
                    "000000000000000000000000000000010000000000000000000000000000000200000000000000000000000000000003");
            byte[] code =
                repeat(
                    x,
                    PUSH1,
                    32,
                    CALLDATALOAD,
                    PUSH1,
                    16,
                    CALLDATALOAD,
                    PUSH1,
                    0,
                    CALLDATALOAD,
                    inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test5HighPreFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {JUMPI};

        for (Instruction inst : instructions) {
            callData =
                    HexUtil.decode(
                            "000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000003");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                    new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test5HighPostFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println(
            "\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println(
            "========================================================================");

        Instruction[] instructions = {JUMPI};

        for (Instruction inst : instructions) {
            callData =
                HexUtil.decode(
                    "000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000003");
            byte[] code =
                repeat(
                    x,
                    PUSH1,
                    32,
                    CALLDATALOAD,
                    PUSH1,
                    16,
                    CALLDATALOAD,
                    PUSH1,
                    0,
                    CALLDATALOAD,
                    inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test6SHA3PreFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {SHA3};

        for (Instruction inst : instructions) {
            callData =
                    HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(x, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst, POP, POP);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                    new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            System.out.println(result);
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test6SHA3PostFork() {
        int x = 64;
        int y = 1000;
        int z = Tier.HIGH.cost();

        System.out.println(
            "\n========================================================================");
        System.out.println("Cost for instructions of the high tier");
        System.out.println(
            "========================================================================");

        Instruction[] instructions = {SHA3};

        for (Instruction inst : instructions) {
            callData =
                HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                repeat(x, PUSH1, 16, CALLDATALOAD, PUSH1, 0, CALLDATALOAD, inst, POP, POP);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            System.out.println(result);
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test7ExpPreFork() {
        int x = 64;
        int y = 1000;
        int z = 10;

        System.out.println(
                "\n========================================================================");
        System.out.println("Cost for instructions of the VeryLow tier");
        System.out.println(
                "========================================================================");

        Instruction[] instructions = {EXP};

        for (Instruction inst : instructions) {
            callData =
                    HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                    repeat(
                            x,
                            PUSH1,
                            32,
                            CALLDATALOAD,
                            PUSH1,
                            16,
                            CALLDATALOAD,
                            PUSH1,
                            0,
                            CALLDATALOAD,
                            inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                    new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPre040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                    "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                    inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test7ExpPostFork() {
        int x = 64;
        int y = 1000;
        int z = 10;

        System.out.println(
            "\n========================================================================");
        System.out.println("Cost for instructions of the VeryLow tier");
        System.out.println(
            "========================================================================");

        Instruction[] instructions = {EXP};

        for (Instruction inst : instructions) {
            callData =
                HexUtil.decode("0000000000000000000000000000000100000000000000000000000000000002");
            byte[] code =
                repeat(
                    x,
                    PUSH1,
                    32,
                    CALLDATALOAD,
                    PUSH1,
                    16,
                    CALLDATALOAD,
                    PUSH1,
                    0,
                    CALLDATALOAD,
                    inst);

            ExecutionContext ctx = newExecutionContext();

            // compile
            FastVmTransactionResult result =
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());

            long t1 = System.nanoTime();
            for (int i = 0; i < y; i++) {
                new FastVM().runPost040Fork(code, ctx, wrapInKernelInterface(repo));
            }
            long t2 = System.nanoTime();

            long c = (t2 - t1) / y / x;
            System.out.printf(
                "%12s: %3d ns per instruction, %3d ms for nrgLimit = %d\n",
                inst.name(), c, (nrgLimit / z) * c / 1_000_000, nrgLimit);
        }
    }

    @Test
    public void test8Remaining() {

        for (Instruction inst : Instruction.values()) {
            if (inst.tier() != Tier.BASE
                    && inst.tier() != Tier.LOW
                    && inst.tier() != Tier.VERY_LOW
                    && inst.tier() != Tier.MID
                    && inst.tier() != Tier.HIGH) {
                System.out.println(inst.name() + "\t" + inst.tier());
            }
        }
    }

    private IExternalStateForFvm wrapInKernelInterface(RepositoryForTesting cache) {
        return new ExternalStateForTesting(
            cache,
            new BlockchainForTesting(),
            blockCoinbase,
            blockDifficulty,
            false,
            true,
            false,
            blockNumber,
            blockTimestamp,
            blockNrgLimit,
            false);
    }

    private ExecutionContext newExecutionContext() {
        return ExecutionContext.from(
                txHash,
                address,
                origin,
                caller,
                nrgPrice,
                nrgLimit,
                callValue,
                callData,
                depth,
                kind,
                flags,
                blockCoinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockDifficulty);
    }
}
