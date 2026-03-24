# VCEManager

VCEManager is an Android application designed to manage and monitor **vce** (Virtual Container Environment) containers on Android devices. It provides a graphical interface for common container operations, resource monitoring, and network management.

## Features

- **Dashboard**: Quick overview of total, running, and stopped containers, plus network status.
- **Container Lifecycle**: Create, start, stop, and delete containers.
- **Real-time Monitoring**: Monitor CPU usage, memory (RSS), network traffic (RX/TX), and process count for individual containers.
- **Resource Limits**: Set CPU usage percentages and assign specific CPU cores to containers.
- **Snapshots**: Save, restore, and delete container snapshots for easy rollbacks.
- **Port Forwarding**: Manage TCP/UDP port forwarding rules between the host and containers.
- **Terminal/Logs**: View container logs and execute shell commands within the container environment.
- **Networking**: Initialize and destroy the virtual bridge network (`vce0`).
- **Autostart**: Toggle autostart settings for containers.

## Requirements

- **Root Access**: The app requires root permissions (via KernelSU or Magisk) to interact with the system and execute the `vce` binary.
- **VCE Binary**: The backend logic depends on the `vce` utility located at `/data/adb/ksu/bin/vce`.
- **Android Version**: Minimum SDK 24 (Android 7.0).

## Technical Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **UI Components**: 
    - Jetpack Navigation Component
    - ViewBinding
    - Material Design Components
    - ViewPager2 with TabLayout
    - SwipeRefreshLayout
- **Asynchronous Programming**: Kotlin Coroutines & Flow
- **Data Persistence**: LiveData for reactive UI updates

## Project Structure

- `com.vcemanager.model`: Data classes for containers, stats, and network info.
- `com.vcemanager.repository`: `VceRepository` handles the shell execution and parsing of `vce` command outputs.
- `com.vcemanager.viewmodel`: Contains `MainViewModel` for global app state and `ContainerDetailViewModel` for specific container operations.
- `com.vcemanager.ui`: All UI fragments including Dashboard, Container list, and multi-tab Detail view.

## Screenshots

*(Add screenshots here once available)*

## License

*(Specify license, e.g., MIT, Apache 2.0)*
