class LogCategory(private val category: String) {

	fun e(ex: Throwable, s: String) {
		println("E/$category $s")
		ex.printStackTrace()
	}

	fun w(ex: Throwable, s: String) {
		println("W/$category $s")
		ex.printStackTrace()
	}

	fun e(s: String) {
		println("E/$category $s")
	}

	fun w(s: String) {
		println("W/$category $s")
	}

	fun i(s: String) {
		println("I/$category $s")
	}

	fun v(stringMaker: () -> String) {
		if (verbose) println("V/$category ${stringMaker()}")
	}
}
