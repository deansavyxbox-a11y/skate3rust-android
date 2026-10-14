use std::path::{Path, PathBuf};

pub(crate) enum MapChoice<'a> { Default, TestWorld, Saved(&'a str) }
pub(crate) fn resolve_startup(root: &Path, choice: MapChoice<'_>) -> Result<(PathBuf, Option<PathBuf>), String> {
    if matches!(choice,MapChoice::TestWorld) {
        let root=root.canonicalize().map_err(|e|e.to_string())?;
        let assets=root.join("assets").canonicalize().map_err(|e|e.to_string())?;
        if !assets.is_dir() || !assets.starts_with(&root) { return Err("Invalid Android assets directory".into()); }
        return Ok((assets,None));
    }
    if let MapChoice::Saved(relative) = choice {
        let path=Path::new(relative);
        if path.parent()==Some(Path::new("maps")) && path.extension().is_some_and(|e|e=="skate") {
            if let Ok((assets,map))=resolve_installation(root,path) { return Ok((assets,Some(map))); }
        }
    }
    resolve_installation(root, Path::new("maps/University.skate")).map(|(assets,map)| (assets,Some(map)))
}

pub(crate) fn resolve_installation(root: &Path, map: &Path) -> Result<(PathBuf, PathBuf), String> {
    if map.as_os_str().is_empty() || !map.components().all(|c| matches!(c, std::path::Component::Normal(_))) {
        return Err("Map must be a relative path inside the Android installation".into());
    }
    let root = root.canonicalize().map_err(|e| format!("Installation {}: {e}", root.display()))?;
    let assets = root.join("assets").canonicalize().map_err(|e| format!("Asset root: {e}"))?;
    let map = root.join(map).canonicalize().map_err(|e| format!("Installed map: {e}"))?;
    if !assets.is_dir() || !map.is_file() || !assets.starts_with(&root) || !map.starts_with(&root) {
        return Err("Assets and map must remain inside the Android installation".into());
    }
    Ok((assets, map))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn saved_map_survives_restart_and_missing_map_falls_back_without_escaping() {
        let root=std::env::temp_dir().join(format!("skate-map-choice-{}",std::process::id()));
        std::fs::create_dir_all(root.join("assets")).unwrap();
        std::fs::create_dir_all(root.join("maps")).unwrap();
        for name in ["University","MegaPark"] {std::fs::write(root.join(format!("maps/{name}.skate")),b"map").unwrap();}
        assert_eq!(resolve_startup(&root,MapChoice::Saved("maps/MegaPark.skate")).unwrap().1,Some(root.join("maps/MegaPark.skate").canonicalize().unwrap()));
        assert_eq!(resolve_startup(&root,MapChoice::TestWorld).unwrap().1,None);
        let fallback=Some(root.join("maps/University.skate").canonicalize().unwrap());
        assert_eq!(resolve_startup(&root,MapChoice::Saved("maps/missing.skate")).unwrap().1,fallback);
        assert_eq!(resolve_startup(&root,MapChoice::Saved("../escape.skate")).unwrap().1,fallback);
        assert_eq!(resolve_startup(&root,MapChoice::Default).unwrap().1,fallback);
        std::fs::remove_dir_all(root).unwrap();
    }
    #[test]
    fn validates_installed_map_and_rejects_missing_or_escaping_paths() {
        let root = std::env::temp_dir().join(format!("skate-android-paths-{}", std::process::id()));
        std::fs::create_dir_all(root.join("assets")).unwrap();
        std::fs::create_dir_all(root.join("maps")).unwrap();
        std::fs::write(root.join("maps/University.skate"), b"test map").unwrap();
        let result = resolve_installation(&root, Path::new("maps/University.skate")).unwrap();
        assert_eq!(result.0, root.join("assets").canonicalize().unwrap());
        assert_eq!(result.1, root.join("maps/University.skate").canonicalize().unwrap());
        assert!(resolve_installation(&root, Path::new("maps/missing.skate")).is_err());
        assert!(resolve_installation(&root, Path::new("../escape.skate")).is_err());
        assert!(resolve_installation(&root, &root.join("maps/University.skate")).is_err());
        assert!(resolve_installation(&root.join("missing"), Path::new("maps/University.skate")).is_err());
        std::fs::remove_dir_all(root).unwrap();
    }
}
