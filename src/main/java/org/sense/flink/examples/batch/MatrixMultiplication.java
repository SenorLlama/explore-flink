package org.sense.flink.examples.batch;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.MapPartitionFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.DiscardingOutputFormat;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.util.Collector;

public class MatrixMultiplication {

	private static final String MATRIX_A = "A";
	private static final String MATRIX_B = "B";

	/**
	 * <code>
	 * matrix A
	 * | 1  3  4 -2|
	 * | 6  2 -3  1|
	 * file matrixA.csv
	 * 1,1,1
	 * 1,2,3
	 * 1,3,4
	 * 1,4,-2
	 * 2,1,6
	 * 2,2,2
	 * 2,3,-3
	 * 2,4,1
	 * 
	 * matrix B
	 * | 1 -2|
	 * | 4  3|
	 * |-3 -2|
	 * | 0  4|
	 * file matrixB.csv
	 * 1,1,1
	 * 1,2,-2
	 * 2,1,4
	 * 2,2,3
	 * 3,1,-3
	 * 3,2,-2
	 * 4,1,0
	 * 4,2,4
	 * 
	 * matrix AB should be
	 * | 1 -9|
	 * |23  4|
	 * 1,1,1
	 * 1,2,-9
	 * 2,1,23
	 * 2,2,4
	 * </code>
	 * 
	 * @throws Exception
	 */
	public MatrixMultiplication() throws Exception {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		DataSet<Tuple4<String, Integer, Integer, Integer>> matrixA = env.readCsvFile("resources/matrixA.csv")
				.fieldDelimiter(",").types(Integer.class, Integer.class, Integer.class)
				.map(t -> new Tuple4<String, Integer, Integer, Integer>("A", t.f0, t.f1, t.f2))
				.returns(Types.TUPLE(Types.STRING, Types.INT, Types.INT, Types.INT));
		System.out.println("Matrix A");
		matrixA.print();

		DataSet<Tuple4<String, Integer, Integer, Integer>> matrixB = env.readCsvFile("resources/matrixB.csv")
				.fieldDelimiter(",").types(Integer.class, Integer.class, Integer.class)
				.map(t -> new Tuple4<String, Integer, Integer, Integer>("B", t.f0, t.f1, t.f2))
				.returns(Types.TUPLE(Types.STRING, Types.INT, Types.INT, Types.INT));
		System.out.println("Matrix B");
		matrixB.print();

		int columnsMatrixB = 2;
		int linesMatrixA = 2;

		// create key and values for both matrix
		DataSet<Tuple2<Tuple3<Integer, Integer, Integer>, Integer>> keyValueMatrixA = matrixA
				.mapPartition(new MapMatrixToKeysAndValues(columnsMatrixB));
		// System.out.println("Matrix A");
		// keyValueMatrixA.print();

		DataSet<Tuple2<Tuple3<Integer, Integer, Integer>, Integer>> keyValueMatrixB = matrixB
				.mapPartition(new MapMatrixToKeysAndValues(linesMatrixA));
		// System.out.println("Matrix B");
		// keyValueMatrixB.print();

		// simple union operation on matrix A and B
		DataSet<Tuple2<Tuple3<Integer, Integer, Integer>, Integer>> matrixAB = keyValueMatrixA.union(keyValueMatrixB);
		// System.out.println("Matrix AB");
		// matrixAB.print();

		// multiply two cells of both matrix. Ex: A[1,1] * B[1,1]
		DataSet<Tuple2<Tuple3<Integer, Integer, Integer>, Integer>> matrixAB_01 = matrixAB.groupBy(0)
				.reduce(new ProductReducer());
		// System.out.println("Matrix AB 01");
		// matrixAB_01.print();

		// transform key(i,k,i+j) and value(AB[i,j]) into key(i,j) and value(AB[i,j])
		DataSet<Tuple2<Tuple2<Integer, Integer>, Integer>> matrixAB_02 = matrixAB_01.map(new SumMapper());
		// System.out.println("Matrix AB 02");
		// matrixAB_02.print();

		DataSet<Tuple2<Tuple2<Integer, Integer>, Integer>> productMatrixAB = matrixAB_02.groupBy(0)
				.reduce(new SumReducer());
		System.out.println("Matrix AB");
		productMatrixAB.print();

		productMatrixAB.output(new DiscardingOutputFormat<Tuple2<Tuple2<Integer, Integer>, Integer>>());

		System.out.println("ExecutionPlan ........................ ");
		System.out.println(env.getExecutionPlan());
		System.out.println("........................ ");
	}

	/**
	 * <code>
	 * matrix A:
	 * for each element (i,j) of matrix A, emit key(i,k,i+j) and value(A[i,j]) for k in 1 until N.
	 * where:
	 * N is the number of columns on matrix B.
	 * 
	 * matrix B:
	 * for each element (j,k) of matrix B, emit key(i,k,j+k) and value(B[j,k]) for i in 1 until L.
	 * where:
	 * L is the number of lines on matrix A.
	 * </code>
	 * 
	 * This method receives matrix A and matrix B in a Tuple2<> and return a
	 * key/value in a tuple Tuple2<>
	 * 
	 * <code>
	 * matrix A = Tuple3<Integer, Integer, Integer>
	 * matrix B = Tuple3<Integer, Integer, Integer>
	 * key returned: Tuple3<Integer, Integer, Integer>
	 * value returned: Integer
	 * </code>
	 * 
	 * @author Felipe Oliveira Gutierrez
	 *
	 */
	public static class MapMatrixToKeysAndValues implements
			MapPartitionFunction<Tuple4<String, Integer, Integer, Integer>, Tuple2<Tuple3<Integer, Integer, Integer>, Integer>> {

		private static final long serialVersionUID = 6992353073599144457L;
		private int count;

		public MapMatrixToKeysAndValues(int count) {
			this.count = count;
		}

		@Override
		public void mapPartition(Iterable<Tuple4<String, Integer, Integer, Integer>> values,
				Collector<Tuple2<Tuple3<Integer, Integer, Integer>, Integer>> out) throws Exception {

			for (Tuple4<String, Integer, Integer, Integer> tuple : values) {
				for (int c = 1; c <= count; c++) {

					Tuple3<Integer, Integer, Integer> key = null;
					Integer value = null;

					if (MATRIX_A.equals(tuple.f0)) {
						// key(i,k,i+j) for k=1...N
						Integer i = tuple.f1;
						Integer j = tuple.f2;
						Integer k = c;
						key = new Tuple3<Integer, Integer, Integer>(i, k, i + j);

						// value matrix[i,j]
						value = tuple.f3;
					} else if (MATRIX_B.equals(tuple.f0)) {
						// key(i,k,i+j) for i=1...L
						Integer i = c;
						Integer j = tuple.f1;
						Integer k = tuple.f2;
						key = new Tuple3<Integer, Integer, Integer>(i, k, i + j);

						// value matrix[j,k]
						value = tuple.f3;
					}
					out.collect(new Tuple2<Tuple3<Integer, Integer, Integer>, Integer>(key, value));
				}
			}
		}
	}

	/**
	 * This function just multiply two cells of both matrix. Ex: A[1,1] * B[1,1]
	 * 
	 * @author Felipe Oliveira Gutierrez
	 *
	 */
	public static class ProductReducer implements ReduceFunction<Tuple2<Tuple3<Integer, Integer, Integer>, Integer>> {

		private static final long serialVersionUID = 6166767956669902083L;

		@Override
		public Tuple2<Tuple3<Integer, Integer, Integer>, Integer> reduce(
				Tuple2<Tuple3<Integer, Integer, Integer>, Integer> value1,
				Tuple2<Tuple3<Integer, Integer, Integer>, Integer> value2) throws Exception {

			Integer product = null;
			product = value1.f1 * value2.f1;

			return new Tuple2<Tuple3<Integer, Integer, Integer>, Integer>(value1.f0, product);
		}
	}

	/**
	 * This function is just to map all cells that are going to being sum on both
	 * matrix.
	 * 
	 * @author Felipe Oliveira Gutierrez
	 *
	 */
	public static class SumMapper implements
			MapFunction<Tuple2<Tuple3<Integer, Integer, Integer>, Integer>, Tuple2<Tuple2<Integer, Integer>, Integer>> {

		private static final long serialVersionUID = -1437482917757334157L;

		@Override
		public Tuple2<Tuple2<Integer, Integer>, Integer> map(Tuple2<Tuple3<Integer, Integer, Integer>, Integer> value)
				throws Exception {

			Tuple2<Integer, Integer> key = new Tuple2<Integer, Integer>(value.f0.f0, value.f0.f1);
			return new Tuple2<Tuple2<Integer, Integer>, Integer>(key, value.f1);
		}
	}

	/**
	 * 
	 * <code>
	 * This is the dot product of the result in 4 reducers because the matrix AB =
	 * | 1 -9|
	 * |23  4|
	 * 
	 * So, the reduces on AB_1,1 will need all the values of A_1,* and B_*,1. 8 values in total.
	 * Reduce function: emit key = (i,k) and value = SUM_j(A[i,j] * B[j,k])
	 * 
	 * </code>
	 * 
	 * @author Felipe Oliveira Gutierrez
	 */
	public static class SumReducer implements ReduceFunction<Tuple2<Tuple2<Integer, Integer>, Integer>> {

		private static final long serialVersionUID = 7849401047616065465L;

		@Override
		public Tuple2<Tuple2<Integer, Integer>, Integer> reduce(Tuple2<Tuple2<Integer, Integer>, Integer> value1,
				Tuple2<Tuple2<Integer, Integer>, Integer> value2) throws Exception {

			Tuple2<Integer, Integer> key = new Tuple2<Integer, Integer>(value1.f0.f0, value1.f0.f1);
			Integer value = value1.f1 + value2.f1;

			return new Tuple2<Tuple2<Integer, Integer>, Integer>(key, value);
		}
	}
}