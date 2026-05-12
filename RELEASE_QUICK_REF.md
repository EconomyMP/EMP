# 🚀 EMP Release Quick Reference

## Fast Track to Release

### For Minor Changes (Bug Fixes)
```bash
# Make changes
git add .
git commit -m "fix: Fix spawner progression bug"
git push origin main
# ✅ CI/CD validates automatically
```

### For New Features (Minor Version Bump)
```bash
# Option A: Manual (recommended)
1. Edit: build.gradle.kts → version = "1.1.0"
2. Run: git add . && git commit -m "chore: bump to 1.1.0" && git push
3. Wait: Auto Tag creates tag automatically
4. Done: Auto Release publishes release

# Option B: One-Click (fastest)
1. Go to: https://github.com/EconomyMP/EMP/actions
2. Select: Version Bump and Release workflow
3. Click: Run workflow
4. Choose: "minor"
5. Wait: Release in ~2 minutes
```

### For Major Updates (Major Version Bump)
```bash
# Same as new features, but:
# In Version Bump workflow, choose: "major"
# Publishes: vX+1.0.0 (e.g., 1.0.0 → 2.0.0)
```

---

## Key URLs

| What | URL |
|------|-----|
| **Repository** | https://github.com/EconomyMP/EMP |
| **Releases** | https://github.com/EconomyMP/EMP/releases |
| **Actions** | https://github.com/EconomyMP/EMP/actions |
| **Version Bump Workflow** | https://github.com/EconomyMP/EMP/actions/workflows/version-bump.yml |
| **Latest Release JAR** | https://github.com/EconomyMP/EMP/releases/latest/download/EMP-core-*.jar |

---

## File to Edit for Release

**Only file needed**: `build.gradle.kts`

```gradle
allprojects {
    group = "github.nighter"
    version = "1.0.0"  ← Change this number
}
```

---

## Commit Message Examples

```
✅ GOOD (will appear in changelog):
feat: Add manufacturing system
fix: Fix spawner progression lag
docs: Update installation guide

❌ NOT GOOD (just chores):
chore: Update deps
chore: Format code
```

---

## Common Tasks

### Download Latest Build
```bash
curl -L https://github.com/EconomyMP/EMP/releases/latest/download/EMP-core-1.0.0.jar
```

### Install Locally
```bash
1. Download JAR from releases page
2. Place in server's plugins/ folder
3. Restart server
4. Type: /mfg register MyShop
```

### Check Current Version
```bash
grep 'version = ' build.gradle.kts | head -1
```

### View Recent Releases
https://github.com/EconomyMP/EMP/releases

---

## Workflow Status

| Workflow | Status | Trigger |
|----------|--------|---------|
| Auto Tag | ✅ Active | `build.gradle.kts` changes |
| Auto Release | ✅ Active | Tag push (`v*`) |
| CI/CD | ✅ Active | Every push/PR |
| Version Bump | ✅ Active | Manual dispatch |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Release not published | Check Actions tab for workflow errors |
| JAR not uploading | Check build output path matches workflow |
| Tag not creating | Ensure `build.gradle.kts` was edited directly |
| Version not detected | Use format: `version = "X.Y.Z"` |

---

## Pro Tips

1. **Three ways to release:**
   - Edit version → Commit → Push (Auto Tag → Auto Release)
   - Use Version Bump workflow (fastest)
   - Manual tag push (git tag v1.1.0 && git push origin v1.1.0)

2. **Always use semantic versioning:**
   ```
   1.0.0 (MAJOR.MINOR.PATCH)
   │ │ └─ Patch: bug fixes
   │ └─── Minor: new features
   └───── Major: breaking changes
   ```

3. **Changelog auto-generates from commit messages:**
   - Use `feat:`, `fix:`, `docs:` prefixes
   - Workflow extracts commits since last tag

---

## One-Command Release (After setup)

```bash
# Edit version in build.gradle.kts, then:
git add . && git commit -m "chore: bump version" && git push origin main
# Auto Tag + Auto Release handles the rest!
```

---

**Still need help?** Check `.github/workflows/README.md` for detailed docs
