# Installation Guide - Nova-Simulator

This guide explains how to install the prerequisites, get the project, compile it,
and run Nova-Simulator.

Nova-Simulator is a JavaFX application built with Maven. The project uses Java 21,
JavaFX 21.0.2, and `jSerialComm` for the optional Arduino hardware mode.

## 1. Install Prerequisites

Install the following tools before running the project:

- Java JDK 21
- Apache Maven
- Git, only if you need to clone the project from a remote repository

Then verify the installation:

```bash
java -version
mvn -version
git --version
```

The `java -version` command should report version 21.

### macOS

With Homebrew:

```bash
brew install openjdk@21 maven git
```

If `java -version` cannot find Java after installation, add Java 21 to your
`PATH`.

On Apple Silicon Macs:

```bash
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

On Intel Macs:

```bash
echo 'export PATH="/usr/local/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### Windows

1. Install a JDK 21 distribution.
2. Install Apache Maven.
3. Add the environment variables:
   - `JAVA_HOME` pointing to the JDK 21 folder.
   - `%JAVA_HOME%\bin` in `Path`.
   - Maven's `bin` folder in `Path`.
4. Open a new PowerShell terminal and verify:

```powershell
java -version
mvn -version
```

### Linux Ubuntu/Debian

```bash
sudo apt update
sudo apt install openjdk-21-jdk maven git
```

Then verify:

```bash
java -version
mvn -version
```

If your distribution does not provide `openjdk-21-jdk`, install a JDK 21 from
your package manager or from a compatible OpenJDK distribution.

## 2. Get the Project

If the project is already on your machine, open the `Nova-Simulator` folder
directly.

Otherwise, clone the repository:

```bash
git clone <REPOSITORY_URL>
cd Nova-Simulator
```

All following commands must be run from the project root, where the `pom.xml`
file is located.

## 3. Download Dependencies and Compile

On the first run, Maven automatically downloads the JavaFX and `jSerialComm`
dependencies.

```bash
mvn clean compile
```

If compilation succeeds, Maven ends with the `BUILD SUCCESS` message.

To generate the project JAR:

```bash
mvn clean package
```

The generated file is placed in the `target/` directory.

## 4. Run the Application

Run Nova-Simulator with the JavaFX Maven plugin:

```bash
mvn javafx:run
```

A window titled `Nova Exoskeleton Simulator` should open.

In the console, you should see messages similar to:

```text
Nova Simulator started with config: ...
Controller: ...
```

## 5. Verify the Application

In the user interface:

1. Click `Play` to start the simulation.
2. Click `Pause` to temporarily stop it.
3. Use `Step` to advance the simulation one frame at a time.
4. Use `Reset` to reset the posture.
5. Change `Height`, `Mass`, `Max Torque`, or `Max Power` to verify that the
   model updates.
6. Use `Export` to save the parameters as a CSV file.
7. Use `Import` to reload an exported CSV file.

## 6. Optional Arduino Hardware Mode

Hardware mode is not required to run the simulation. It is used to control one
joint through Arduino serial input.

Expected configuration:

- Serial connection at `9600 baud`
- Potentiometer value between `0` and `1023`
- Accepted serial lines:
  - `POT:512`
  - `512`
  - `BUTTON_1`
  - `BUTTON_2`
  - `START`

In the application:

1. Open the `Mode & Hardware` section.
2. Select `HARDWARE` mode.
3. Choose the joint to control: `HIP`, `KNEE`, or `ANKLE`.
4. Click `Refresh Ports`.
5. Select the Arduino serial port.
6. Set the minimum and maximum angles.
7. Click `Connect`.

`BUTTON_1` switches to the next joint. `BUTTON_2` switches to the next camera
mode.

## 7. Common Issues

### `Unable to locate a Java Runtime`

Java is not installed, or JDK 21 is not available in your `PATH`.

Check:

```bash
java -version
```

Then install or configure Java 21.

### `mvn: command not found`

Maven is not installed, or Maven's `bin` directory is not available in your
`PATH`.

Check:

```bash
mvn -version
```

### Maven cannot download dependencies

Check your internet connection and run:

```bash
mvn clean compile
```

### No serial port appears

Check that:

- the Arduino is connected;
- the USB cable supports data transfer;
- the Arduino IDE serial monitor is closed;
- the user has the required serial permissions on Linux.

On Linux, you may need to add the user to the `dialout` group:

```bash
sudo usermod -a -G dialout $USER
```

Log out and log back in after running this command.

### The serial port is already in use

Close applications that may be using the Arduino, such as the Arduino IDE, a
serial monitor, or another simulator.

## 8. Clean the Project

To remove Maven-generated files:

```bash
mvn clean
```
