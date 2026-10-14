//! Renderer-free validation used by setup and import flows.
use std::{fs, path::{Path, PathBuf}};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ValidationReport { pub maps: Vec<String>, pub files: usize }
pub mod xdvdfs;

pub fn validate_installation(root: &Path) -> Result<ValidationReport, String> {
    let root = root.canonicalize().map_err(|e| format!("Cannot open installation: {e}"))?;
    let game = skate_data::GameAssets::load(&root.join("assets")).map_err(|e| e.to_string())?;
    if game.initial_animation.trim().is_empty() { return Err("Missing initial animation".into()); }
    let complete: serde_json::Value = serde_json::from_slice(&fs::read(root.join("complete.json")).map_err(|e| format!("Missing complete manifest: {e}"))?).map_err(|e| format!("Invalid complete manifest: {e}"))?;
    let maps = complete.get("maps").and_then(serde_json::Value::as_array).ok_or("Complete manifest has no map catalog")?;
    if maps.is_empty() { return Err("Installation contains no maps".into()); }
    let mut names = Vec::new();
    for map in maps {
        let name = map.as_str().ok_or("Invalid map name")?;
        if name.is_empty() || name.len() > 100 || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-') || names.iter().any(|n| n == name) { return Err(format!("Invalid or duplicate map name: {name}")); }
        let relative = PathBuf::from("maps").join(format!("{name}.skate"));
        let path = root.join(&relative);
        if path.canonicalize().map_err(|e| format!("Map {name} is missing: {e}"))?.strip_prefix(&root).is_err() { return Err(format!("Map {name} escapes the installation")); }
        let data = fs::read(&path).map_err(|e| format!("Cannot read map {name}: {e}"))?;
        skate_data::skate_map::SkateMap::parse(&data).map_err(|e| format!("Invalid map {name}: {e}"))?;
        names.push(name.to_owned());
    }
    let files = complete.get("files").and_then(serde_json::Value::as_u64).unwrap_or(0) as usize;
    Ok(ValidationReport { maps: names, files })
}

pub fn validate_map_bytes(data: &[u8]) -> Result<(), String> {
    skate_data::skate_map::SkateMap::parse(data).map(|_| ()).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn absent_core_installation_is_rejected() {
        let root = std::env::temp_dir().join(format!("skate-import-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root); fs::create_dir_all(&root).unwrap();
        assert!(validate_installation(&root).unwrap_err().contains("game.json"));
        fs::remove_dir_all(root).unwrap();
    }
    #[test] fn malformed_map_is_rejected_without_panicking() {
        assert!(validate_map_bytes(b"SKATE01").is_err());
    }
}
