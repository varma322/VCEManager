package com.vcemanager.ui.containers

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.vcemanager.MainActivity
import com.vcemanager.R
import com.vcemanager.databinding.*
import com.vcemanager.model.*
import com.vcemanager.viewmodel.*

// ── Container List ────────────────────────────────────────────
class ContainersFragment : Fragment() {

    private var _binding: FragmentContainersBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: ContainerAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContainersBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ContainerAdapter(
            onStart = { c ->
                if (c.cmd.isBlank()) showStartDialog(c.name)
                else vm.startContainer(c.name, c.cmd) { ok, msg ->
                    (activity as? MainActivity)?.showSnack(msg, !ok)
                }
            },
            onStop  = { c -> vm.stopContainer(c.name) { ok, msg ->
                (activity as? MainActivity)?.showSnack(msg, !ok) }
            },
            onDetail = { c ->
                // Note: Directions might not be generated yet or might be under old package
                // findNavController().navigate(ContainersFragmentDirections.actionContainersToDetail(c.name))
                val bundle = Bundle().apply { putString("containerName", c.name) }
                findNavController().navigate(R.id.action_containers_to_detail, bundle)
            },
            onDelete = { c -> confirmDelete(c) }
        )

        binding.recyclerContainers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@ContainersFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        binding.swipeRefresh.setOnRefreshListener { vm.refresh() }
        vm.loading.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it == true }
        vm.containers.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_containers_to_createContainer)
        }
    }

    private fun showStartDialog(name: String) {
        val input = EditText(requireContext()).apply {
            hint = "Command (e.g. nginx -g 'daemon off;')"
            setText("tail -f /dev/null")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Start $name")
            .setMessage("Enter the command to run:")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                vm.startContainer(name, input.text.toString()) { ok, msg ->
                    (activity as? MainActivity)?.showSnack(msg, !ok)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(c: Container) {
        if (c.isRunning) {
            (activity as? MainActivity)?.showSnack("Stop the container first", true)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${c.name}?")
            .setMessage("This will permanently delete the container and its rootfs.")
            .setPositiveButton("Delete") { _, _ ->
                vm.deleteContainer(c.name) { ok, msg ->
                    (activity as? MainActivity)?.showSnack(msg, !ok)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Container Detail ──────────────────────────────────────────
class ContainerDetailFragment : Fragment() {

    private var _binding: FragmentContainerDetailBinding? = null
    private val binding get() = _binding!!
    private val mainVm: MainViewModel by activityViewModels()
    private val detailVm: ContainerDetailViewModel by viewModels()
    // Using manual args if directions are broken
    // private val args: ContainerDetailFragmentArgs by navArgs()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContainerDetailBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name = arguments?.getString("containerName") ?: ""

        binding.toolbarDetail.apply {
            title = name
            setNavigationOnClickListener { findNavController().popBackStack() }
        }

        detailVm.load(name)

        // Setup ViewPager tabs
        val tabAdapter = ContainerDetailPagerAdapter(this, name, mainVm, detailVm)
        binding.viewPager.adapter = tabAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Overview"
                1 -> "Ports"
                2 -> "Stats"
                3 -> "Snapshots"
                4 -> "Terminal"
                else -> ""
            }
        }.attach()

        // Header — status chip
        mainVm.containers.observe(viewLifecycleOwner) { containers ->
            val c = containers.find { it.name == name } ?: return@observe
            binding.statusChip.apply {
                text = if (c.isRunning) "Running • ${c.ip}" else "Stopped"
                chipBackgroundColor = resources.getColorStateList(
                    if (c.isRunning) R.color.status_running else R.color.status_stopped,
                    requireContext().theme
                )
            }
            binding.btnStartStop.apply {
                text = if (c.isRunning) "Stop" else "Start"
                setOnClickListener {
                    if (c.isRunning) {
                        mainVm.stopContainer(name) { ok, msg ->
                            (activity as? MainActivity)?.showSnack(msg, !ok)
                        }
                    } else {
                        mainVm.startContainer(name, c.cmd) { ok, msg ->
                            (activity as? MainActivity)?.showSnack(msg, !ok)
                        }
                    }
                }
            }
            binding.switchAutostart.isChecked = c.autostart
        }

        binding.switchAutostart.setOnCheckedChangeListener { _, checked ->
            mainVm.setAutostart(name, checked)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Create Container Dialog ───────────────────────────────────
class CreateContainerFragment : Fragment() {

    private var _binding: FragmentCreateContainerBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    // Storage presets in MB
    private val storageOptions = listOf(
        "2 GB"  to 2048,
        "4 GB"  to 4096,
        "6 GB"  to 6144,
        "8 GB"  to 8192,
        "12 GB" to 12288,
        "16 GB" to 16384,
        "Custom" to -1
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentCreateContainerBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // Storage spinner
        val storageLabels = storageOptions.map { it.first }
        val spinnerAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, storageLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerStorage.adapter = spinnerAdapter
        binding.spinnerStorage.setSelection(1) // default 4GB

        binding.spinnerStorage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val isCustom = storageOptions[pos].second == -1
                binding.layoutCustomStorage.visibility = if (isCustom) View.VISIBLE else View.GONE
            }
        }

        // Rootfs tarball presets
        val rootfsOptions = listOf(
            "Ubuntu 22.04 (arm64)" to "/data/local/tmp/ubuntu-base-22.04-base-arm64.tar.gz",
            "Ubuntu 22.04 (armhf)" to "/data/local/tmp/ubuntu-base-22.04-base-armhf.tar.gz",
            "Custom path..." to ""
        )
        val rootfsLabels = rootfsOptions.map { it.first }
        val rootfsAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, rootfsLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerRootfs.adapter = rootfsAdapter

        binding.spinnerRootfs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val path = rootfsOptions[pos].second
                if (path.isEmpty()) {
                    binding.layoutCustomRootfs.visibility = View.VISIBLE
                    binding.editCustomRootfs.text?.clear()
                } else {
                    binding.layoutCustomRootfs.visibility = View.GONE
                    binding.editCustomRootfs.setText(path)
                }
            }
        }

        binding.btnCreate.setOnClickListener {
            val name = binding.editName.text.toString().trim()
            if (name.isBlank()) {
                binding.editName.error = "Name required"
                return@setOnClickListener
            }
            if (!name.matches(Regex("[a-zA-Z0-9_-]+"))) {
                binding.editName.error = "Only letters, numbers, - and _"
                return@setOnClickListener
            }

            val selectedStorage = storageOptions[binding.spinnerStorage.selectedItemPosition]
            val storageMb = if (selectedStorage.second == -1) {
                binding.editCustomStorageGb.text.toString().toFloatOrNull()
                    ?.let { (it * 1024).toInt() } ?: 4096
            } else selectedStorage.second

            val tarball = if (binding.layoutCustomRootfs.visibility == View.VISIBLE)
                binding.editCustomRootfs.text.toString().trim()
            else {
                val pos = binding.spinnerRootfs.selectedItemPosition
                rootfsOptions[pos].second
            }

            if (tarball.isBlank()) {
                (activity as? MainActivity)?.showSnack("Tarball path required", true)
                return@setOnClickListener
            }

            vm.createContainer(name, tarball, storageMb) { ok, msg ->
                (activity as? MainActivity)?.showSnack(msg, !ok)
                if (ok) findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
