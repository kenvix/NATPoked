//--------------------------------------------------
// Class SampleTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.client.traversal.SymmetricalNATPoissonTraversalBroker
import com.kenvix.natpoked.client.traversal.expectedValuePortGuess
import com.kenvix.natpoked.client.traversal.poissonPortGuess
import org.apache.commons.math3.distribution.PoissonDistribution
import org.junit.jupiter.api.Test

class SampleTest {
    @Test
    fun poissonSamplingTest() {
        println("PSM: " + poissonPortGuess(0.1, 100,800, 50005, 50))
        println("EVM: " + expectedValuePortGuess(0.1, 100,800, 50005, 50))
    }

    @Test
    fun expectedValueTest() {

    }
}

//fun main() {
//    println(PoissonDistribution(0.1, 50000000.0).sample(100).contentToString())
//}