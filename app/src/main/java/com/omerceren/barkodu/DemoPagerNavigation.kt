// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.omerceren.barkodu

import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.job

// Adapted from compose-miuix-ui/miuix Example MainPagerState (Apache-2.0).
// Keep tap selection immediate, and follow currentPage while the user swipes.
@Stable
internal class DemoPagerNavigation(private val pager: PagerState, private val scope: CoroutineScope) {
    var selectedPage by mutableIntStateOf(pager.currentPage)
        private set
    private var navigating by mutableStateOf(false)
    private var job: Job? = null

    fun animateToPage(index: Int) {
        if(index==selectedPage)return
        job?.cancel()
        selectedPage=index
        navigating=true
        job=scope.launch {
            val myJob=coroutineContext.job
            try {pager.animateScrollToPage(index,animationSpec=spring(dampingRatio=0.9f,stiffness=550f))}
            finally {if(job==myJob){navigating=false;selectedPage=pager.currentPage}}
        }
    }
    fun syncPage() {if(!navigating)selectedPage=pager.currentPage}
}
