package com.vcemanager.ui.containers

import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.*
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vcemanager.MainActivity
import com.vcemanager.R
import com.vcemanager.databinding.*
import com.vcemanager.model.*
import com.vcemanager.viewmodel.*

// ── ViewPager adapter for container detail tabs ───────────────
class ContainerDetailPagerAdapter(
    fragment: Fragment,
    private val name: String,
    private val mainVm: MainViewModel,
    private val detailVm: ContainerDetailViewModel
) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 5
    override fun createFragment(pos: Int): Fragment = when (pos) {
        0 -> ContainerOverviewFragment.newInstance(name)
        1 -> ContainerPortsFragment.newInstance(name)
        2 -> ContainerStatsFragment.newInstance(name)
        3 -> ContainerSnapshotsFragment.newInstance(name)
        4 -> ContainerTerminalFragment.newInstance(name)
        else -> throw IllegalArgumentException("Invalid tab $pos")
    }
}

// ── Overview Tab ──────────────────────────────────────────────
class ContainerOverviewFragment : Fragment() {
    private var _b: FragmentContainerOverviewBinding? = null
    private val b get() = _b!!
    private val mainVm: MainViewModel by activityViewModels()
    private val detailVm: ContainerDetailViewModel by activityViewModels()

    companion object {
        fun newInstance(name: String) = ContainerOverviewFragment().apply {
            arguments = Bundle().apply { putString("name", name) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContainerOverviewBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = arguments?.getString("name") ?: return

        mainVm.containers.observe(viewLifecycleOwner) { containers ->
            val c = containers.find { it.name == name } ?: return@observe
            b.tvName.text    = c.name
            b.tvIp.text      = c.ip.ifBlank { "Not assigned" }
            b.tvCreated.text = c.created
            b.tvCmd.text     = c.cmd.ifBlank { "Default shell" }
            b.tvStorage.text = if (c.storageMb > 0) "${c.storageMb / 1024} GB allocated" else "Default"
            b.tvCpuLimit.text = c.limitCpu.ifBlank { "None" }
            b.tvCpuCores.text = c.limitCpus.ifBlank { "All cores" }

            // Set limits UI
            b.sliderCpu.value = c.limitCpu.toFloatOrNull() ?: 100f
            b.editCpuCores.setText(c.limitCpus)
        }

        b.btnApplyLimits.setOnClickListener {
            val cpu = b.sliderCpu.value.toInt()
            val cores = b.editCpuCores.text.toString().trim()
            detailVm.setLimits(name,
                if (cpu < 100) cpu else null,
                cores.ifBlank { null }
            ) { ok, msg -> (activity as? MainActivity)?.showSnack(msg, !ok) }
        }

        b.btnClearLimits.setOnClickListener {
            detailVm.clearLimits(name) { ok, msg ->
                (activity as? MainActivity)?.showSnack(msg, !ok)
            }
        }

        b.sliderCpu.addOnChangeListener { _, value, _ ->
            b.tvCpuPercent.text = "${value.toInt()}%"
        }

        b.btnViewLogs.setOnClickListener {
            detailVm.loadLogs(name)
            // Navigate to terminal tab
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Ports Tab ─────────────────────────────────────────────────
class ContainerPortsFragment : Fragment() {
    private var _b: FragmentContainerPortsBinding? = null
    private val b get() = _b!!
    private val detailVm: ContainerDetailViewModel by activityViewModels()

    companion object {
        fun newInstance(name: String) = ContainerPortsFragment().apply {
            arguments = Bundle().apply { putString("name", name) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContainerPortsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = arguments?.getString("name") ?: return

        val adapter = PortRuleAdapter { rule ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete port forward?")
                .setMessage("Remove ${rule.hostPort} → ${rule.containerPort}/${rule.proto}?")
                .setPositiveButton("Delete") { _, _ ->
                    detailVm.delPort(name, rule) { ok, msg ->
                        (activity as? MainActivity)?.showSnack(msg, !ok)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        b.recyclerPorts.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerPorts.adapter = adapter

        detailVm.ports.observe(viewLifecycleOwner) { ports ->
            adapter.submitList(ports)
            b.emptyPorts.visibility = if (ports.isEmpty()) View.VISIBLE else View.GONE
        }

        b.btnAddPort.setOnClickListener { showAddPortDialog(name) }

        b.btnFlushPorts.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Flush all ports?")
                .setMessage("Remove all port forwards for $name?")
                .setPositiveButton("Flush") { _, _ ->
                    detailVm.flushPorts(name) { ok, msg ->
                        (activity as? MainActivity)?.showSnack(msg, !ok)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showAddPortDialog(name: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_port, null)
        val editHost      = dialogView.findViewById<EditText>(R.id.editHostPort)
        val editContainer = dialogView.findViewById<EditText>(R.id.editContainerPort)
        val radioTcp      = dialogView.findViewById<android.widget.RadioButton>(R.id.radioTcp)
        radioTcp.isChecked = true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Port Forward")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val host = editHost.text.toString().trim()
                val cont = editContainer.text.toString().trim()
                val proto = if (radioTcp.isChecked) "tcp" else "udp"
                if (host.isBlank() || cont.isBlank()) {
                    (activity as? MainActivity)?.showSnack("Ports required", true)
                    return@setPositiveButton
                }
                detailVm.addPort(name, host, cont, proto) { ok, msg ->
                    (activity as? MainActivity)?.showSnack(msg, !ok)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Stats Tab ─────────────────────────────────────────────────
class ContainerStatsFragment : Fragment() {
    private var _b: FragmentContainerStatsBinding? = null
    private val b get() = _b!!
    private val detailVm: ContainerDetailViewModel by activityViewModels()

    companion object {
        fun newInstance(name: String) = ContainerStatsFragment().apply {
            arguments = Bundle().apply { putString("name", name) }
        }
    }

    private val cpuHistory = mutableListOf<Float>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContainerStatsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = arguments?.getString("name") ?: return

        detailVm.startStatsPolling(name)

        detailVm.stats.observe(viewLifecycleOwner) { stats ->
            stats ?: return@observe

            b.tvCpuPct.text    = "${stats.cpuPercent.format(1)}%"
            b.tvMemRss.text    = "${stats.memRssMb} MB"
            b.tvNetRx.text     = "${stats.netRxKb} KB"
            b.tvNetTx.text     = "${stats.netTxKb} KB"
            b.tvUptime.text    = stats.uptime
            b.tvProcs.text     = stats.processCount.toString()
            b.tvCpuLimit.text  = "${stats.cpuLimit}%"
            b.tvCpuCores.text  = stats.cpuCores
            b.tvPid.text       = stats.pid

            // CPU sparkline
            cpuHistory.add(stats.cpuPercent)
            if (cpuHistory.size > 30) cpuHistory.removeAt(0)
            b.progressCpu.progress = stats.cpuPercent.toInt()
        }
    }

    override fun onPause() {
        super.onPause()
        detailVm.stopStatsPolling()
    }

    override fun onResume() {
        super.onResume()
        val name = arguments?.getString("name") ?: return
        detailVm.startStatsPolling(name)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
}

// ── Snapshots Tab ─────────────────────────────────────────────
class ContainerSnapshotsFragment : Fragment() {
    private var _b: FragmentContainerSnapshotsBinding? = null
    private val b get() = _b!!
    private val detailVm: ContainerDetailViewModel by activityViewModels()

    companion object {
        fun newInstance(name: String) = ContainerSnapshotsFragment().apply {
            arguments = Bundle().apply { putString("name", name) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContainerSnapshotsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = arguments?.getString("name") ?: return

        val adapter = SnapshotAdapter(
            onRestore = { snap ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Restore to '${snap.tag}'?")
                    .setMessage("Container will be stopped and restored. Current state will be lost unless you save a snapshot first.")
                    .setPositiveButton("Restore") { _, _ ->
                        detailVm.restoreSnapshot(name, snap.tag) { ok, msg ->
                            (activity as? MainActivity)?.showSnack(msg, !ok)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onDelete = { snap ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete snapshot '${snap.tag}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        detailVm.deleteSnapshot(name, snap.tag) { ok, msg ->
                            (activity as? MainActivity)?.showSnack(msg, !ok)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        b.recyclerSnapshots.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerSnapshots.adapter = adapter

        detailVm.snapshots.observe(viewLifecycleOwner) { snaps ->
            adapter.submitList(snaps)
            b.emptySnapshots.visibility = if (snaps.isEmpty()) View.VISIBLE else View.GONE
        }

        b.btnSaveSnapshot.setOnClickListener {
            val input = EditText(requireContext()).apply {
                hint = "e.g. v1, before-upgrade, working-nginx"
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Save Snapshot")
                .setMessage("Enter a tag for this snapshot:")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val tag = input.text.toString().trim()
                    if (tag.isBlank()) {
                        (activity as? MainActivity)?.showSnack("Tag required", true)
                        return@setPositiveButton
                    }
                    (activity as? MainActivity)?.showSnack("Saving snapshot '$tag'...")
                    detailVm.saveSnapshot(name, tag) { ok, msg ->
                        (activity as? MainActivity)?.showSnack(msg, !ok)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Terminal Tab ──────────────────────────────────────────────
class ContainerTerminalFragment : Fragment() {
    private var _b: FragmentContainerTerminalBinding? = null
    private val b get() = _b!!
    private val detailVm: ContainerDetailViewModel by activityViewModels()
    private val mainVm: MainViewModel by activityViewModels()

    companion object {
        fun newInstance(name: String) = ContainerTerminalFragment().apply {
            arguments = Bundle().apply { putString("name", name) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContainerTerminalBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = arguments?.getString("name") ?: return

        // Load logs on open
        detailVm.loadLogs(name)
        detailVm.logs.observe(viewLifecycleOwner) { logs ->
            b.tvOutput.text = logs.ifBlank { "(no output yet)" }
            b.scrollOutput.post { b.scrollOutput.fullScroll(View.FOCUS_DOWN) }
        }

        b.btnRefreshLogs.setOnClickListener { detailVm.loadLogs(name) }

        b.btnExec.setOnClickListener {
            val cmd = b.editCommand.text.toString().trim()
            if (cmd.isBlank()) return@setOnClickListener

            // Check container is running first
            val container = mainVm.containers.value?.find { it.name == name }
            if (container?.isRunning != true) {
                (activity as? MainActivity)?.showSnack("Container is not running", true)
                return@setOnClickListener
            }

            b.tvOutput.append("\n\$ $cmd\n")
            detailVm.execCommand(name, cmd) { ok, result ->
                b.tvOutput.append(if (ok) result else "Error: $result")
                b.tvOutput.append("\n")
                b.scrollOutput.post { b.scrollOutput.fullScroll(View.FOCUS_DOWN) }
            }
            b.editCommand.text?.clear()
        }

        // Quick command shortcuts
        val quickCmds = listOf("ps aux", "df -h", "free -m", "ip addr", "nginx -t", "python3 --version")
        b.chipGroupQuick.removeAllViews()
        quickCmds.forEach { cmd ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = cmd
                isClickable = true
                setOnClickListener { b.editCommand.setText(cmd) }
            }
            b.chipGroupQuick.addView(chip)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── RecyclerView Adapters ─────────────────────────────────────
class ContainerAdapter(
    private val onStart: (Container) -> Unit,
    private val onStop: (Container) -> Unit,
    private val onDetail: (Container) -> Unit,
    private val onDelete: (Container) -> Unit
) : ListAdapter<Container, ContainerAdapter.VH>(ContainerDiff()) {

    inner class VH(val b: ItemContainerBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContainerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = getItem(pos)
        h.b.apply {
            tvName.text   = c.name
            tvIp.text     = c.ip.ifBlank { "No IP" }
            tvStatus.text = c.status.replaceFirstChar { it.uppercase() }
            tvStatus.setTextColor(root.context.getColor(
                if (c.isRunning) R.color.status_running else R.color.status_stopped))
            tvPid.text = if (c.isRunning) "PID: ${c.pid}" else ""
            btnStartStop.text = if (c.isRunning) "Stop" else "Start"
            btnStartStop.setOnClickListener { if (c.isRunning) onStop(c) else onStart(c) }
            root.setOnClickListener { onDetail(c) }
            btnDelete.setOnClickListener { onDelete(c) }
            ivAutostart.visibility = if (c.autostart) View.VISIBLE else View.GONE
        }
    }
}

class ContainerDiff : DiffUtil.ItemCallback<Container>() {
    override fun areItemsTheSame(a: Container, b: Container) = a.name == b.name
    override fun areContentsTheSame(a: Container, b: Container) = a == b
}

class PortRuleAdapter(
    private val onDelete: (PortRule) -> Unit
) : ListAdapter<PortRule, PortRuleAdapter.VH>(PortDiff()) {

    inner class VH(val b: ItemPortRuleBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPortRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val r = getItem(pos)
        h.b.apply {
            tvHostPort.text      = ":${r.hostPort}"
            tvArrow.text         = "→"
            tvContainerPort.text = ":${r.containerPort}"
            tvProto.text         = r.proto.uppercase()
            btnDelete.setOnClickListener { onDelete(r) }
        }
    }
}

class PortDiff : DiffUtil.ItemCallback<PortRule>() {
    override fun areItemsTheSame(a: PortRule, b: PortRule) =
        a.hostPort == b.hostPort && a.proto == b.proto
    override fun areContentsTheSame(a: PortRule, b: PortRule) = a == b
}

class SnapshotAdapter(
    private val onRestore: (Snapshot) -> Unit,
    private val onDelete: (Snapshot) -> Unit
) : ListAdapter<Snapshot, SnapshotAdapter.VH>(SnapDiff()) {

    inner class VH(val b: ItemSnapshotBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSnapshotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = getItem(pos)
        h.b.apply {
            tvTag.text     = s.tag
            tvDate.text    = s.date
            tvSize.text    = s.size
            tvCmd.text     = s.cmd.take(40)
            btnRestore.setOnClickListener { onRestore(s) }
            btnDelete.setOnClickListener { onDelete(s) }
        }
    }
}

class SnapDiff : DiffUtil.ItemCallback<Snapshot>() {
    override fun areItemsTheSame(a: Snapshot, b: Snapshot) = a.tag == b.tag
    override fun areContentsTheSame(a: Snapshot, b: Snapshot) = a == b
}

// ── Dashboard recent adapter ──────────────────────────────────
class RecentContainerAdapter(
    private val items: List<Container>,
    private val onClick: (Container) -> Unit
) : RecyclerView.Adapter<RecentContainerAdapter.VH>() {

    inner class VH(val b: ItemContainerMiniBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContainerMiniBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        h.b.apply {
            tvName.text = c.name
            tvIp.text = c.ip.ifBlank { "-" }
            // statusDot.setBackgroundResource(
            //    if (c.isRunning) R.drawable.dot_green else R.drawable.dot_red)
            root.setOnClickListener { onClick(c) }
        }
    }
}
