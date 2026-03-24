package com.vcemanager.ui.dashboard

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.vcemanager.MainActivity
import com.vcemanager.R
import com.vcemanager.databinding.FragmentDashboardBinding
import com.vcemanager.viewmodel.MainViewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentDashboardBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener {
            vm.refresh()
        }

        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading == true
        }

        vm.containers.observe(viewLifecycleOwner) { containers ->
            val running = containers.count { it.isRunning }
            val stopped = containers.count { !it.isRunning }
            val total   = containers.size

            binding.cardTotal.statValue.text = total.toString()
            binding.cardTotal.statLabel.text = "Total"
            binding.cardRunning.statValue.text = running.toString()
            binding.cardRunning.statLabel.text = "Running"
            binding.cardStopped.statValue.text = stopped.toString()
            binding.cardStopped.statLabel.text = "Stopped"

            // Recent containers list
            // val recentAdapter = RecentContainerAdapter(containers.take(5)) { container ->
            //    val action = DashboardFragmentDirections
            //        .actionDashboardToDetail(container.name)
            //    findNavController().navigate(action)
            // }
            // binding.recyclerRecent.adapter = recentAdapter
        }

        vm.networkInfo.observe(viewLifecycleOwner) { info ->
            binding.networkStatusChip.apply {
                text = if (info.bridgeUp) "Network Active (${info.bridgeIp})" else "Network Down"
                chipBackgroundColor = resources.getColorStateList(
                    if (info.bridgeUp) R.color.status_running else R.color.status_stopped,
                    requireContext().theme
                )
            }
        }

        binding.btnNetworkInit.setOnClickListener {
            vm.networkInit { success, msg ->
                (activity as? MainActivity)?.showSnack(msg, !success)
            }
        }

        binding.btnBoot.setOnClickListener {
            vm.boot { success, msg ->
                (activity as? MainActivity)?.showSnack(
                    if (success) "Boot sequence complete" else msg, !success
                )
            }
        }

        binding.fabNewContainer.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_createContainer)
        }

        binding.btnViewAll.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_containers)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
