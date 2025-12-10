package com.example.myapplication

import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.example.myapplication.databinding.NavbarBinding

class NavbarHelper(
    private val binding: NavbarBinding,
    private val onNavigationItemSelected: (Int) -> Unit = {},
    private val onFabClicked: () -> Unit = {}
) {

    private val colorActive: Int
    private val colorInactive: Int

    init {
        val context = binding.root.context
        colorActive = ContextCompat.getColor(context, R.color.pastel_green)
        colorInactive = ContextCompat.getColor(context, R.color.gray_999)

        setupClickListeners()
    }

    private fun setupClickListeners() = with(binding) {
        navHome.setOnClickListener { selectNavItem(0); onNavigationItemSelected(0) }
        navTimeline.setOnClickListener { selectNavItem(1); onNavigationItemSelected(1) }

        // QR FAB (posisi 2)
        fabAdd.setOnClickListener { onFabClicked() }

        // USER
        navUser.setOnClickListener { selectNavItem(3); onNavigationItemSelected(3) }

        // TUTORIAL
        navProfile.setOnClickListener { selectNavItem(4); onNavigationItemSelected(4) }
    }

    fun selectNavItem(position: Int) {
        resetAllNavItems()

        when (position) {
            0 -> {
                binding.iconHome.setColorFilter(colorActive)
                binding.labelHome.setTextColor(colorActive)
                binding.labelHome.setTypeface(null, Typeface.BOLD)
            }

            1 -> {
                binding.iconTimeline.setColorFilter(colorActive)
                binding.labelTimeline.setTextColor(colorActive)
                binding.labelTimeline.setTypeface(null, Typeface.BOLD)
            }

            3 -> {
                binding.iconUser.setColorFilter(colorActive)
                binding.labelUser.setTextColor(colorActive)
                binding.labelUser.setTypeface(null, Typeface.BOLD)
            }

            4 -> {
                binding.iconProfile.setColorFilter(colorActive)
                binding.labelProfile.setTextColor(colorActive)
                binding.labelProfile.setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun resetAllNavItems() = with(binding) {
        listOf(
            iconHome,
            iconTimeline,
            iconUser,
            iconProfile
        ).forEach { it.setColorFilter(colorInactive) }

        listOf(
            labelHome,
            labelTimeline,
            labelUser,
            labelProfile
        ).forEach {
            it.setTextColor(colorInactive)
            it.setTypeface(null, Typeface.NORMAL)
        }
    }
}
