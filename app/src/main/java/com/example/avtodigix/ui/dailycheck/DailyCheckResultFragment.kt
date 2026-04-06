package com.example.avtodigix.ui.dailycheck

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.avtodigix.R
import com.example.avtodigix.databinding.FragmentDailyCheckResultBinding

class DailyCheckResultFragment : Fragment(R.layout.fragment_daily_check_result) {
    private var _binding: FragmentDailyCheckResultBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDailyCheckResultBinding.bind(view)

        binding.dailyCheckCtaButton.setOnClickListener {
            findNavController().navigate(R.id.action_dailyCheckResultFragment_to_dataFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
