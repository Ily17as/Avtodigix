package com.example.avtodigix.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.avtodigix.R
import com.example.avtodigix.databinding.FragmentHistoryTabBinding
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.ScanSnapshotRepository
import kotlinx.coroutines.launch

class ReportsHistoryFragment : Fragment(R.layout.fragment_history_tab) {
    private var _binding: FragmentHistoryTabBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: ScanSnapshotRepository
    private val adapter = HistoryReportsAdapter(
        onOpen = { snapshot ->
            val intent = Intent(requireContext(), ReportActivity::class.java)
                .putExtra(ReportActivity.EXTRA_SNAPSHOT_ID, snapshot.id)
            startActivity(intent)
        },
        onShare = { snapshot ->
            val report = buildSnapshotReport(requireContext(), snapshot)
            val shareIntent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_history_title))
                .putExtra(Intent.EXTRA_TEXT, appendSiteLinkIfMissing(report))
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHistoryTabBinding.bind(view)
        repository = ScanSnapshotRepository(AppDatabase.create(requireContext()).scanSnapshotDao())
        binding.historyRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val history = repository.getHistory()
            adapter.submit(history)
            binding.historyEmpty.isVisible = history.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
