# GitHub Actions Automation

This directory contains automated workflows for EMP (Economy Multiplayer) plugin development and release management.

## 🚀 Available Workflows

### 1. **Auto Version + Tag** (`auto-tag.yml`)
**Triggers:** On every push to `main` (except bot-generated pushes)

**What it does:**
- Reads commit history since the previous tag
- Calculates semantic bump automatically:
   - `major` for `BREAKING CHANGE` or `type!:` commits
   - `minor` for `feat:` commits
   - `patch` otherwise
- Updates `build.gradle.kts` version automatically
- Commits the version bump and creates/pushes a new tag (e.g., `v1.0.1`)

**When to use:** Push to `main` → version/tag are generated automatically → Auto Release follows

---

### 2. **Auto Release** (`auto-release.yml`)
**Triggers:** When a tag matching `v*` is pushed

**What it does:**
- Builds the project with Gradle
- Generates changelog from commits since previous tag
- Creates GitHub Release with:
  - Release notes with features list
  - Changelog from commit messages
  - JAR artifact upload
- Automatically publishes to GitHub Releases

**When to use:** Auto triggered after Auto Tag creates a tag

---

### 3. **CI/CD Pipeline** (`ci-cd.yml`)
**Triggers:** On every push to `main`/`develop` and pull requests

**What it does:**
- Builds project on Java 21
- Validates Gradle wrapper
- Runs tests and uploads reports
- Integrates with SonarCloud for code quality analysis (optional)

**When to use:** Continuous validation during development

---

### 4. **Version Bump and Release** (`version-bump.yml`)
**Triggers:** Manual workflow dispatch

**How to use:**
1. Go to **Actions** tab on GitHub
2. Select "Version Bump and Release"
3. Click "Run workflow"
4. Choose version bump type:
   - **major**: 1.0.0 → 2.0.0
   - **minor**: 1.0.0 → 1.1.0
   - **patch**: 1.0.0 → 1.0.1
5. Workflow will:
   - Update `build.gradle.kts`
   - Commit changes
   - Create and push tag
   - Trigger Auto Release automatically

**When to use:** Fastest way to release new versions without manual steps

---

## 📋 Release Process (Automated)

### Method 1: Fully Automatic (Recommended)
```
1. Push commits to main
2. Auto Version + Tag computes next semver from commit history
3. Workflow updates build.gradle.kts and pushes vX.Y.Z tag
4. Auto Release creates release automatically
```

### Method 2: Workflow Dispatch (Recommended)
```
1. Go to GitHub Actions → Version Bump and Release
2. Click "Run workflow"
3. Select version type (major/minor/patch)
4. Watch it create release automatically
```

---

## ⚙️ Configuration

### Required GitHub Settings

1. **Branch Protection** (optional but recommended)
   - Settings → Branches → Add rule for `main`
   - Require status checks to pass (CI/CD Pipeline)

2. **Secrets** (optional for SonarCloud)
   - Settings → Secrets and variables → Actions
   - Add `SONAR_TOKEN` if using SonarCloud integration

3. **Token Permissions**
   - Actions already have `contents: write` permission
   - No additional setup required

### Version Format
- Must follow semantic versioning: `MAJOR.MINOR.PATCH`
- Example: `1.0.0`, `1.0.1`, `1.1.0`, `2.0.0`

---

## 📦 Release Artifacts

Each release includes:
- **JAR File**: `EMP-core-X.Y.Z.jar`
- **Release Notes**: With changelog and features
- **GitHub Release**: Available in Releases tab

### Download Releases
```bash
# Latest release
curl -L https://github.com/EconomyMP/EMP/releases/latest/download/EMP-core-1.0.0.jar

# Specific version
curl -L https://github.com/EconomyMP/EMP/releases/download/v1.0.0/EMP-core-1.0.0.jar
```

---

## 🔄 Workflow Sequence

```
Scenario 1: Update version manually
┌─────────────────────────────┐
│ Edit build.gradle.kts       │
│ Commit & Push to main       │
└──────────────┬──────────────┘
               ↓
┌──────────────────────────────┐
│ Auto Tag Workflow            │
│ (Triggered by file change)   │
│ ✓ Creates tag v1.0.0         │
│ ✓ Pushes to origin           │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│ Auto Release Workflow        │
│ (Triggered by tag push)      │
│ ✓ Builds project             │
│ ✓ Creates release            │
│ ✓ Uploads artifacts          │
└──────────────────────────────┘
```

```
Scenario 2: Use Version Bump workflow
┌─────────────────────────────┐
│ Click "Run workflow" button  │
│ Select version type: patch  │
└──────────────┬──────────────┘
               ↓
┌──────────────────────────────┐
│ Version Bump Workflow        │
│ ✓ Updates build.gradle.kts   │
│ ✓ Commits changes            │
│ ✓ Creates & pushes tag       │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│ Auto Tag Workflow            │
│ (Skips - tag already exists) │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│ Auto Release Workflow        │
│ (Triggered by tag push)      │
│ ✓ Builds project             │
│ ✓ Creates release            │
│ ✓ Uploads artifacts          │
└──────────────────────────────┘
```

---

## ✅ Checklist for First Release

- [ ] Workflows are in `.github/workflows/` directory
- [ ] All Java code committed and pushed to main
- [ ] Version in `build.gradle.kts` is set correctly
- [ ] Go to GitHub Actions tab
- [ ] Select "Version Bump and Release" workflow
- [ ] Click "Run workflow" and select version type
- [ ] Wait for workflow to complete
- [ ] Check Releases tab for new release

---

## 🐛 Troubleshooting

### Auto Release fails to upload JAR
- **Cause**: JAR path mismatch
- **Fix**: Check build output path matches workflow configuration
- **Alternative**: Enable `continue-on-error: true` to create release even if upload fails

### Auto Tag doesn't trigger
- **Cause**: File path filtering not matching
- **Check**: Push changes to `build.gradle.kts` directly to main
- **Debug**: Check Actions tab for workflow runs

### Version format incorrect
- **Cause**: Non-semantic versioning
- **Fix**: Use format `X.Y.Z` (e.g., `1.0.0`)
- **Invalid**: `1.0`, `v1.0.0`, `1.0.0-alpha`

---

## 📝 Commit Message Format

For better changelog generation, use:
```
feat: Add new feature description
fix: Fix bug description
docs: Documentation changes
refactor: Code refactoring
perf: Performance improvements
test: Add tests
chore: Build/dependency updates
```

Example:
```
feat: Add manufacturing system
fix: Fix spawner progression bug
docs: Update README with manufacturing guide
```

---

## 🔗 Related Files
- `build.gradle.kts` - Version source of truth
- `.github/workflows/` - Automation configuration
- `README.md` - Main documentation

---

## 📞 Support
For issues with workflows, check:
1. GitHub Actions tab → Recent runs
2. Workflow logs for error details
3. Ensure branch protection rules aren't blocking automation
