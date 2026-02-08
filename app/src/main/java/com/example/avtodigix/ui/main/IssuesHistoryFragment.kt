package com.example.avtodigix.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.avtodigix.R
import com.example.avtodigix.databinding.FragmentIssuesHistoryBinding
import com.google.android.material.tabs.TabLayoutMediator

class IssuesHistoryFragment : Fragment(R.layout.fragment_issues_history) {
    private var _binding: FragmentIssuesHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentIssuesHistoryBinding.bind(view)

        val adapter = IssuesHistoryPagerAdapter(requireActivity())
        binding.issuesHistoryPager.adapter = adapter

        TabLayoutMediator(binding.issuesHistoryTabs, binding.issuesHistoryPager) { tab, position ->
            tab.text = if (position == 0) {
                getString(R.string.tab_dtc)
            } else {
                getString(R.string.tab_reports)
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class IssuesHistoryPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return if (position == 0) {
            IssuesTabFragment()
        } else {
            ReportsHistoryFragment()
        }
    }
}
