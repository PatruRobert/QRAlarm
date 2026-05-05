import subprocess
import sys
import os


def run_cmd(command):
    """Runs a shell command and handles errors gracefully."""
    try:
        # shell=True is sometimes needed for Windows gradlew commands,
        # but list format is generally safer.
        result = subprocess.run(command, check=True, text=True, capture_output=True)
        print(result.stdout.strip())
        return True
    except subprocess.CalledProcessError as e:
        print(f"\n❌ Error running: {' '.join(command)}")
        print(e.stderr.strip())
        return False


def main():
    print("=== QRAlarm Auto-Sync & Release ===")

    # 1. Check if there is anything to commit
    status = subprocess.run(
        ["git", "status", "--porcelain"], capture_output=True, text=True
    )

    # 2. Get the commit message and version
    commit_msg = input("\nEnter commit description: ").strip()
    if not commit_msg:
        print("Commit canceled: Message cannot be empty.")
        sys.exit(1)

    version_tag = input(
        "Enter release version (e.g., v1.0.1) or press Enter to skip release: "
    ).strip()

    # 3. Compile the APK (Only if we are making a release)
    apk_path = (
        "app/build/outputs/apk/debug/app-debug.apk"  # Default Android Studio path
    )
    if version_tag:
        print("\n🔨 Compiling the APK (this might take a minute)...")
        # Use "gradlew.bat" if on Windows, "./gradlew" if on Mac/Linux
        gradle_cmd = (
            ["gradlew.bat", "assembleDebug"]
            if os.name == "nt"
            else ["./gradlew", "assembleDebug"]
        )
        if not run_cmd(gradle_cmd):
            print("Failed to build APK. Aborting release.")
            sys.exit(1)

    # 4. Execute the Git pipeline
    if status.stdout.strip():
        print("\n📦 Staging files (git add .)...")
        if not run_cmd(["git", "add", "."]):
            sys.exit(1)

        print(f"\n💾 Committing (git commit -m '{commit_msg}')...")
        if not run_cmd(["git", "commit", "-m", commit_msg]):
            sys.exit(1)

        print("\n🚀 Pushing to remote (git push)...")
        if not run_cmd(["git", "push"]):
            sys.exit(1)
    else:
        print("\nNo code changes to push, but continuing to release if requested.")

    # 5. Create the GitHub Release and upload the APK
    if version_tag:
        print(f"\n🏷️ Creating GitHub Release {version_tag}...")

        # Check if the APK was actually created
        if not os.path.exists(apk_path):
            print(f"❌ Error: Could not find compiled APK at {apk_path}")
            sys.exit(1)

        # gh release create <tag> <files...> --title <title> --notes <notes>
        gh_cmd = [
            "gh",
            "release",
            "create",
            version_tag,
            apk_path,
            "--title",
            f"Release {version_tag}",
            "--notes",
            commit_msg,
        ]

        if run_cmd(gh_cmd):
            print(
                f"\n✅ Successfully created release {version_tag} with downloadable APK!"
            )
        else:
            sys.exit(1)
    else:
        print("\n✅ Successfully synced with remote repository! (No release created)")


if __name__ == "__main__":
    main()
