package test.kotlin.script

import test.kotlin.script.common.test1
import java.util.concurrent.Callable

class Script : Callable<String> {
    override fun call(): String {
        return test1()
        //return  test2()// <- works fine in fail scenario
    }
}