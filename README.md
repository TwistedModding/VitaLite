# VitaLite

VitaLite is an enhanced RuneLite client loader that provides additional functionality, safety features, and customization options while maintaining compatibility with the core RuneLite experience.

## What is VitaLite?

VitaLite is a RuneLite client wrapper that adds extra features and safety options while keeping the familiar RuneLite experience. It provides additional customization, enhanced security features, and extended plugin support.

## Key Features

### 🎮 Enhanced Gaming Experience
- **Plugin Support**: Load additional plugins and external extensions
- **Packet Logging**: Monitor game network traffic for analysis
- **Headless Mode**: Run the client without display for resource saving
- **Console Access**: Built-in logging and debugging tools

### 🛡️ Safety & Privacy Features
- **Incognito Mode**: Appears as regular RuneLite for privacy
- **Identity Protection**: Identified 1:1 as RuneLite itself
- **Detection Avoidance**: Enhanced safety protections

### 🎨 Interface Improvements
- **Enhanced Settings**: Additional configuration options
- **Console Integration**: Real-time logging display
- **Navigation Enhancements**: Improved UI components

## Command Line Options

VitaLite supports several command-line arguments for customization:

### Core Options

| Option       | Type | Description                                                                  |
|--------------|------|------------------------------------------------------------------------------|
| `--rsdump`   | String | Path to dump the gamepack to (optional)                                      |
| `-noPlugins` | Boolean | Disables loading of core plugins                                             |
| `-min`       | Boolean | Runs jvm with minimal alotted memory. Only works if `-noPlugins` is enabled. |
| `-incognito` | Boolean | Visually display as 'RuneLite' instead of 'VitaLite'                         |

### Usage Examples

```bash
# Launch VitaLite normally
java -jar VitaLite-shaded.jar

# Launch in incognito mode (appears as RuneLite)
java -jar VitaLite-shaded.jar -incognito

# Launch without core plugins
java -jar VitaLite-shaded.jar -noPlugins

# Dump gamepack and launch in incognito mode
java -jar VitaLite-shaded.jar --rsdump /path/to/dump -incognito

# Get help information
java -jar VitaLite-shaded.jar -help
```

### File Structure

```
~/.runelite/
├── repository2/           # RuneLite artifacts
├── externalplugins/       # External plugin JARs
└── sideloaded-plugins/    # External plugin JARs
```

## Building

### Prerequisites
- Java 11

### Build Commands

```bash
# First, sync the RuneLite API (required before building)
./gradlew syncRuneLiteAPI

# Clean and build all modules
./gradlew clean build

# Build with dependencies (fat JAR)
./gradlew shadowJar

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Clean and publish everything
./gradlew cleanAndPublishAll
```

**Important**: You must run `./gradlew syncRuneLiteAPI` before building to ensure the RuneLite API dependencies are properly synchronized.

The main executable will be located at `build/libs/VitaLite-*-shaded.jar`.

## Getting Started

1. **Download**: Get the latest VitaLite release
2. **Install**: No installation needed - it's a standalone JAR file
3. **Launch**: Double-click the JAR or use command line options
4. **Configure**: Use in-client settings or command line arguments

## Plugin Support

VitaLite supports loading additional plugins:

- Place plugin JAR files in `~/.runelite/externalplugins/`
- Or use the `~/.runelite/sideloaded-plugins/` directory
- Plugins will be automatically loaded on startup

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Disclaimer

VitaLite is a third-party modification of RuneLite. Use at your own risk. The developers are not responsible for any consequences resulting from the use of this software.