import subprocess
import sys
import os

# --- THE IRONCLAD ENVIRONMENT OVERRIDE ---
# We make a copy of your system variables and forcefully overwrite JAVA_HOME
custom_env = os.environ.copy()
custom_env["JAVA_HOME"] = (
    r"C:\Users\rroob\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"
)


def run_cmd(command):
    """Runs a shell command and streams the output directly to the terminal."""
    try:
        # Notice we pass 'env=custom_env' so Gradle cannot use the VS Code extension's path
        subprocess.run(command, check=True, env=custom_env)
        return True
    except subprocess.CalledProcessError:
        print(f"\n❌ Error running command.")
        return False


def main():
    print("=== QAlarm Auto-Installer ===")
    print(f"🔒 Forcing Java Path: {custom_env['JAVA_HOME']}")
    print("🔨 Compiling and pushing to connected tablet...\n")

    # 1. Determine the correct Gradle command
    gradle_cmd = (
        ["gradlew.bat", "installDebug"]
        if os.name == "nt"
        else ["./gradlew", "installDebug"]
    )

    # 2. Run the build and install process
    if run_cmd(gradle_cmd):
        print("\n✅ Successfully pushed to tablet!")

        # Uncomment these lines if you want the app to auto-open on the tablet
        # package_name = "com.patrurobert.qalarm"
        # adb_cmd = ["adb", "shell", "monkey", "-p", package_name, "-c", "android.intent.category.LAUNCHER", "1"]
        # print(f"🚀 Launching {package_name} on tablet...")
        # run_cmd(adb_cmd)

    else:
        print("\n⚠️ Failed to install. Check your tablet connection.")
        sys.exit(1)


if __name__ == "__main__":
    main()
