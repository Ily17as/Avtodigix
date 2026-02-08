package com.example.avtodigix.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.avtodigix.MainActivity
import com.example.avtodigix.R
import com.example.avtodigix.databinding.FragmentIssuesTabBinding
import com.example.avtodigix.storage.AppDatabase
import com.example.avtodigix.storage.ScanSnapshot
import com.example.avtodigix.storage.ScanSnapshotRepository
import com.example.avtodigix.ui.RecommendationsBottomSheetDialogFragment
import kotlinx.coroutines.launch

class IssuesTabFragment : Fragment(R.layout.fragment_issues_tab) {
    private var _binding: FragmentIssuesTabBinding? = null
    private val binding get() = _binding!!
    private val dtcAdapter = DtcItemAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentIssuesTabBinding.bind(view)
        val viewModel = (requireActivity() as MainActivity).connectionViewModel

        binding.dtcRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.dtcRecycler.adapter = dtcAdapter

        binding.saveReportButton.setOnClickListener {
            val state = viewModel.obdState.value
            val repository = ScanSnapshotRepository(AppDatabase.create(requireContext()).scanSnapshotDao())
            viewLifecycleOwner.lifecycleScope.launch {
                repository.saveSnapshot(
                    ScanSnapshot(
                        timestampMillis = System.currentTimeMillis(),
                        keyMetrics = buildKeyMetrics(state),
                        dtcList = (state.storedDtcs + state.pendingDtcs).distinct()
                    )
                )
            }
        }

        binding.shareButton.setOnClickListener {
            val state = viewModel.obdState.value
            val reportText = buildCurrentDtcReport(requireContext(), state.storedDtcs, state.pendingDtcs)
            shareReport(reportText)
        }

        binding.clearErrorsButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.clear_errors_confirm)
                .setPositiveButton(R.string.action_clear_errors) { _, _ ->
                    viewModel.clearDtcs()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.whatToDoButton.setOnClickListener {
            RecommendationsBottomSheetDialogFragment.forDiagnostics().show(
                childFragmentManager,
                RecommendationsBottomSheetDialogFragment.TAG
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.obdState.collect { state ->
                    binding.dtcSummaryValue.text = getString(
                        R.string.dtc_summary_format,
                        state.storedDtcs.size,
                        state.pendingDtcs.size
                    )
                    dtcAdapter.submit(state.storedDtcs, state.pendingDtcs)
                }
            }
        }
    }

    private fun shareReport(content: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_current_title))
            .putExtra(Intent.EXTRA_TEXT, content)
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
