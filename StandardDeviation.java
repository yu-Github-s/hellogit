package standardDivition;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * 標準偏差を求めるクラス
 * 
 * @author 脇本悠希
 *
 */
public class StandardDeviation {
	// TODO 仕様書と違う処理を行う場合、自己判断せず、必ず相談する
	// GRPコードの入力桁数チェック
	private static final String GRP_CODE_SIZE = "[0-9]{11}";
	// 拠点コードの入力桁数チェック
	private static final String LOCATION_CODE_SIZE = "[0-9]{6}";
	// PreparedStatementで使用
	private static final int ONE = 1;
	// PreparedStatementで使用_2 
	private static final int TWO = 2;
	// PreparedStatementで使用_3 
	private static final int THREE = 3;
	// オリジナルGRPコードを取得するSQL
	// TODO 文字列はひとまとめ（preparedStatement）にして"?"による置き換えを行う
	private static final String ORIGINAL_GRP_SQL = "SELECT DISTINCT ORG_GRPC " + "FROM T_LIFE_DEMAND_WORK_DATA "
			+ "WHERE GRPC = ? " + "AND FLOW_FG = 2 " + "AND DELETE_FG = 0";
	// オリジナルGRPコードをもとに「過去6ヶ月」,「過去36ヶ月」の月別受注実績を検索SQL
	// TODO 文字列はひとまとめ（preparedStatement）にして"?"による置き換えを行う
	private static final String GET_ORD_TAKE_QTY = "SELECT ORD_TAKE_QTY "
			+ "FROM T_ORDER_TAKE_HIST_M " + "WHERE LOCATION_CD = ? " + "AND GRPC = ? "
			+ "AND ORDER_YM = TO_CHAR(add_months(sysdate, - ?),'YYYYMM')" + "AND DELETE_FG = 0 "
			+ "AND ORD_TAKE_QTY >= 1";
	// TODO doubleである必要性は？割る数なだけなら整数でよいのでは？
	// 受注実績合計の平均を求める時に使用
	private static final int AVERAGE = 36;
	// 標準偏差計算処理メソッドで小数点第二位の計算時に使用
	private static final int SECOND_DECIMAL_POINT = 10;
	// 標準偏差計算処理メソッドで値を二乗する際に使用
	private static final int SQUARED = 2;
	// 各月別に（xi-Xバー)の2乗の合計を除算時に使用
	private static final int SQUARED_TOTAL_DIVIDE = 35;

	// TODO メッセージも定数化できるものは定数にする

	/**
	 * mainメソッド
	 * 
	 * @param args
	 *            (0)拠点コード、(1)GRPコード
	 * 
	 * @author 脇本
	 */
	public static void main(String[] args) {
		// TODO 入力チェックに関して確認を取る。
		// 拠点コード
		String locationCode = args[0];
		// GRPコード
		String grpCode = args[1];
		// 拠点コードが未入力の場合
		if (StringUtils.isEmpty(locationCode)) {
			System.err.print("拠点コードは入力必須です。");
		}
		// 拠点コードが6桁で入力されていない場合
		if (!(locationCode.matches(LOCATION_CODE_SIZE))) {
			// メッセージを追加
			System.err.print("拠点コードは6桁で入力してください");
		}
		// GRPコードが未入力の場合
		if (StringUtils.isEmpty(grpCode)) {
			System.err.print("GRPコードは入力必須です。 ");
		}
		// GRPコードが11桁で入力されていない場合
		if (!(grpCode.matches(GRP_CODE_SIZE))) {
			System.err.print("GRPコードは11桁で入力してください");
		}
		// TODO エラー時にこの後の処理には進まないようにする（入力方法を変える）
		// DB接続情報呼び出し(DB接続開始)
		Connection con = null;
		try {
			Class.forName("oracle.jdbc.driver.OracleDriver");
			// データベースへの接続を確立するため「DriverManager」クラスで用意されている"getConnection"メソッドを使う
			con = DriverManager.getConnection("jdbc:oracle:thin:@//localhost:1521/XE", "USER01", "PASSWORD");
			// 受注実績チェック処理メソッドの呼び出し
			ordersReceivedCheck(grpCode, locationCode, con);
			// 処理完了メッセージの表示
			System.out.println("受注実績チェック処理は正常に完了しました。");
			// 受注実績取得処理メソッドの呼び出し
			int[] ordersReceived = receivedOrdersResults(grpCode, locationCode, con);
			// 処理完了メッセージの表示
			System.out.println("受注実績取得処理は正常に完了しました。");
			// 標準偏差として格納する変数を宣言
			// TODO 設計書上Number型でスネークケースなので、そのまま使用してOK
			Number OUT_STD_DEVIATION = 0;
			// 標準偏差計算メソッドの呼び出し
			OUT_STD_DEVIATION = standardDeviationCalculation(ordersReceived);
			// 処理完了メッセージの表示
			System.out.println("標準偏差計算処理は正常に完了しました。");
			// 結果表示
			System.out.println("標準偏差は　：" + OUT_STD_DEVIATION);
		} catch (SQLException se) {
			// SQLExceptionの場合はエラーコードを表示
			System.out.println("SQLのエラーが考えられます" + ((SQLException) se).getErrorCode());
		}catch(ClassNotFoundException ce){
			// TODO ClassNotFoundExceptionのエラーコードも表示させる（エラーコードの取得方法は調べる）
			System.out.println("指定された名前のクラスが見つかりません"+ (ce.getMessage()));
		} finally {
			try {
				if (con != null) {
					// DB接続の終了
					con.close();
				}
			} catch (SQLException e) {
				System.out.println("SQLのエラーが考えられます" + ((SQLException) e).getErrorCode());
			}
		}
	}

	/**
	 * 受注実績チェック処理メソッド
	 * 
	 * @param groCode
	 *            GRPコード
	 * @param locationCode
	 *            拠点コード
	 * @param con
	 *            DB接続情報
	 * @throws SQLException
	 * @throws RuntimeException
	 * @author 脇本
	 */
	public static void ordersReceivedCheck(String grpCode, String locationCode, Connection con) throws SQLException {
		// 処理開始メッセージの表示
		System.out.println("受注実績チェック処理を開始します。");
		// 各月の受注実績を格納するリスト
		List<Integer> ordersReceived = new ArrayList<>();
		// TODO PreparedstatementによるSQL修正を行う
		// オリジナルGRPコード取得のSQL
		String originalGrpCd = ORIGINAL_GRP_SQL;
		// SQL文をDBに送るためのオブジェクトを生成
		PreparedStatement statement = con.prepareStatement(originalGrpCd);
		// 1つ目の?に拠点コードをセット
		statement.setString(ONE, grpCode);
		// TODO PreparedStatementとResultSetを使った場合は、都度close()する「try/catch,finallyで囲む」
		// クエリ結果を格納
		ResultSet grpCdResult = statement.executeQuery();
		// 取得したGRPコードの件数分繰り返す。
		while (grpCdResult.next()) {
			// 取得したオリジナルGRPコードをもとに過去6ヶ月の月別受注実績を検索
			for (int i = 1; i < 6; i++) {
				// 月別受注実績を取得するSQL（拠点コード、GRPコード、num）
				// TODO preparedStatementを使用することで修正が必要
				String ordTakeQty = GET_ORD_TAKE_QTY;
				statement = con.prepareStatement(ordTakeQty);
				// 1つ目の?に拠点コードをセット
				statement.setString(ONE, locationCode);
				// 2つ目の?にGRPコードをセット
				statement.setString(TWO, grpCode);
				// 3つ目の?に現在月からさかのぼる数（i）をセット
				statement.setLong(THREE, i);
				ResultSet ordTakeQtyResult = statement.executeQuery();
				// 取得した月別受注実績の件数分繰り返す。
				while (ordTakeQtyResult.next()) {
					ordersReceived.add(ordTakeQtyResult.getInt("ORD_TAKE_QTY"));
				}
			}
		}
		// 取得したオリジナルGRPコードをもとに過去6ヶ月の月別受注実績が存在しない場合、RuntimeExceptionを発生させる。
		if (ordersReceived.size() < 1) {
			throw new RuntimeException("過去６ヶ月の月別受注実績が存在しません。");
		}
	}

	/**
	 * 受注実績取得処理メソッド
	 * 
	 * @param grpCode
	 *            画面に入力されたGRPコード
	 * @param locationCode
	 *            画面に入力された拠点コード
	 * @param con
	 *            DB接続情報
	 * @return ordersReceived 過去36カ月分の月別受注実績リスト
	 * @throws SQLException
	 * @author 脇本
	 */
	public static int[] receivedOrdersResults(String grpCode, String locationCode, Connection con)
			throws SQLException {
		// 処理開始メッセージの表示
		System.out.println("受注実績取得処理を開始します。");
		// TODO preparedStatementにより修正予定
		// オリジナルGRPコード取得するSQLを実行（SQL文のGRPコードを画面に入力されたGRPコードに置き換える）
		// TODO 変数名（SQL文を取得しているのであってオリジナルGRPコードを取得しているわけではない）
		String sql = ORIGINAL_GRP_SQL;
		PreparedStatement statement = con.prepareStatement(sql);
		// 1つ目の?にGRPコードをセット
		statement.setString(ONE, grpCode);
		ResultSet result = statement.executeQuery();
		// クエリで取得したオリジナルGRPコードを格納するリストを宣言
		List<String> originalGrpCodeList = new ArrayList<>();
		// クエリで取得したオリジナルGRPコードの数だけ格納する処理を繰り返す
		while (result.next()) {
			String originalGrpCdList = result.getString("ORG_GRPC");
			originalGrpCodeList.add(originalGrpCdList);
		}
		// 各月の受注実績数を格納するリストの宣言
		int ordersReceived [] = new int[36] ;
		// 過去36ヶ月の月別受注実績を検索。
		for (int i = 1; i <= 36; i++) {
			// 月別受注実績を格納する変数の宣言
			// TODO int型で宣言すれば、デフォルト値0で使える
			int monthlyOrdersReceived = 0;
			for (String originalGrpCode : originalGrpCodeList) {
				// TODO preparedStatementで修正予定
				String ordTakeQty = GET_ORD_TAKE_QTY;
				PreparedStatement state = con.prepareStatement(ordTakeQty);
				// 1つ目の?に拠点コードをセット
				state.setString(ONE, locationCode);
				// 2つ目の?にGRPコードをセット
				state.setString(TWO, originalGrpCode);
				// 3つ目の?に現在月からさかのぼる数（i）をセット
				state.setLong(THREE, i);
				ResultSet ordTakeQtyResult = state.executeQuery();
				// 受注実績数量を求める（36ヶ月分）
				// TODO 1行だけの取得ならwhileいらないのでは？
				while(ordTakeQtyResult.next()){
					// TODO getIntメソッドではnullは0が返ってくる(確認する)
					monthlyOrdersReceived += ordTakeQtyResult.getInt("ORD_TAKE_QTY");
				}
			}
			// 各月の受注実績数をリストに追加する。
			ordersReceived[i]=monthlyOrdersReceived;
		}
		return ordersReceived;
	}

	/**
	 * 標準偏差計算処理メソッド
	 * 
	 * @param ordersReceivedList
	 *            過去36カ月分の月別受注実績を詰めたリスト
	 * 
	 * @return standardDeviationCalculation 過去36カ月分の月別受注実績の標準偏差
	 * @author 脇本
	 */
	// TODO IntegerはNULLが入ってくる可能性があり、それを考慮しないのであれば、Intのほうが良い場合もある
	public static Number standardDeviationCalculation(int[] ordersReceived) {
		// 処理開始メッセージの表示
		System.out.println("標準偏差計算処理を開始します。");
		// 受注実績合計値
		double total = 0d;
		// 1.合計(36ヶ月分の受注実績合計のリストを全て加算する)
		for (int value : ordersReceived) {
			total += value;
		}
		// 1で求めた受注実績合計の平均を求める
		double average = total / AVERAGE;
		// 小数点第二位以下を四捨五入し、結果を保持。（変数Xバー)
		average = Math.round(average * SECOND_DECIMAL_POINT) / SECOND_DECIMAL_POINT;
		// ２乗合計値で使用する変数の宣言
		double squaredTotal = 0d;
		// 各月別に（xi-Xバー)の2乗を求め、結果を保持。（xiは1～36ヶ月までの各受注実績）
		for (int value : ordersReceived) {
			// 結果を合計する。
			squaredTotal = squaredTotal + Math.pow(value - average, SQUARED);
		}
		// 求めた合計値を’35’で除算
		squaredTotal = squaredTotal / SQUARED_TOTAL_DIVIDE;
		// 除算した値の平方根（ルート）を求める。
		double squareRoot = Math.sqrt(squaredTotal);
		// 標準偏差の値を格納する変数の宣言
		double standardDeviationCalculation = 0d;
		// 結果を小数点第二位以下で四捨五入し、結果を”OUT_STD_DEVIATION"に格納する。（過去36ヶ月の標準偏差）
		standardDeviationCalculation = Math.round(squareRoot * SECOND_DECIMAL_POINT) / SECOND_DECIMAL_POINT;
		// 標準偏差（standardDeviationCalculation）を戻り値に設定
		return standardDeviationCalculation;
	}
}
