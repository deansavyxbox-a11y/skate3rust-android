//! Installed map discovery and the default pointer, shared by startup and the menu.
use std::path::{Path, PathBuf};

#[derive(serde::Deserialize)]
#[serde(deny_unknown_fields)]
struct ParkIndex {version:u32, maps:std::collections::HashMap<String,ParkEntry>}
#[derive(serde::Deserialize)]
#[serde(deny_unknown_fields)]
struct ParkEntry {file:String,base_fingerprint:u64,supplement_fingerprint:u64}

pub(crate) fn load_map(assets:&Path,path:&Path) -> Result<(skate_data::skate_map::SkateMap,u64),String> {
    attach_map(assets,path,skate_data::skate_map::SkateMap::load(path)?)
}

pub(crate) fn attach_map(assets:&Path,path:&Path,map:skate_data::skate_map::SkateMap)
    -> Result<(skate_data::skate_map::SkateMap,u64),String> {
    let base=crate::config::map_fingerprint(Some(path))?;
    let root=assets.join("private/park-layouts");
    let bytes=match std::fs::read(root.join("catalog.json")) {
        Ok(b)=>b, Err(e) if e.kind()==std::io::ErrorKind::NotFound=>return Ok((map,base)),
        Err(e)=>return Err(format!("Park catalog: {e}")),
    };
    let index:ParkIndex=serde_json::from_slice(&bytes).map_err(|e|format!("Park catalog: {e}"))?;
    if index.version!=1 {return Err("Unsupported park catalog version".into());}
    let name=path.file_stem().and_then(|s|s.to_str()).ok_or("Invalid map filename")?;
    let Some(entry)=index.maps.get(name) else {return Ok((map,base));};
    if entry.file!=format!("{name}.skate") || entry.base_fingerprint!=base {
        return Err("Park package does not match the installed base map".into());
    }
    let extra_path=root.join(&entry.file);
    let extra=crate::config::map_fingerprint(Some(&extra_path))?;
    if extra!=entry.supplement_fingerprint {return Err("Park package fingerprint mismatch".into());}
    let map=map.with_supplement(skate_data::skate_map::SkateMap::load(&extra_path)?)?;
    // Bind multiplayer identity to both packages, leaving legacy identities
    // unchanged for installations without supplemental data.
    let combined=base.to_le_bytes().into_iter().chain(extra.to_le_bytes())
        .fold(0xcbf29ce484222325u64,|h,b|(h^u64::from(b)).wrapping_mul(0x100000001b3));
    Ok((map,combined))
}

#[derive(Clone, Debug)]
pub(crate) struct Entry {
    pub label: String,
    pub path: Option<PathBuf>,
}

pub(crate) fn discover(assets: &Path) -> Vec<Entry> {
    let mut maps = Vec::new();
    let root = assets.parent().unwrap_or(assets).join("maps");
    for directory in [&root, &root.join("private")] {
        if let Ok(entries) = std::fs::read_dir(directory) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_file() && path.extension().is_some_and(|e| e.eq_ignore_ascii_case("skate")) {
                    maps.push(Entry {
                        label: path.file_stem().unwrap().to_string_lossy().replace('_', " "),
                        path: Some(path.canonicalize().unwrap_or(path)),
                    });
                }
            }
        }
    }
    maps.sort_by(|a, b| a.label.to_lowercase().cmp(&b.label.to_lowercase()));
    maps.insert(0, Entry { label: "Test world".into(), path: None });
    maps
}

pub(crate) fn default_map(assets: &Path) -> Result<Option<PathBuf>, String> {
    // Only a completed release installation creates this pointer. Existing
    // development checkouts continue to boot the test world.
    let pointer = assets.parent().unwrap_or(assets).join("settings/default-map.json");
    let bytes = match std::fs::read(&pointer) {
        Ok(bytes) => bytes,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(format!("{}: {e}", pointer.display())),
    };
    let relative: Option<String> = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
    let Some(relative) = relative else { return Ok(None); };
    let relative = Path::new(&relative);
    if relative.is_absolute() || relative.components().any(|c| !matches!(c, std::path::Component::Normal(_))) {
        return Err("Invalid installed default map path".into());
    }
    Ok(Some(assets.parent().unwrap_or(assets).join(relative)))
}

/// Save only a successfully activated map. Null explicitly selects the test world.
pub(crate) fn save_default(assets: &Path, map: Option<&Path>) -> Result<(), String> {
    let root = assets.parent().unwrap_or(assets);
    let relative = map.map(|path| path.strip_prefix(root)
        .map(|p| p.to_string_lossy().replace('\\', "/"))
        .map_err(|_| "Map is outside this installation; default map unchanged".to_string())).transpose()?;
    let directory = root.join("settings");
    std::fs::create_dir_all(&directory).map_err(|e| e.to_string())?;
    let temporary = directory.join("default-map.json.tmp");
    std::fs::write(&temporary, serde_json::to_vec_pretty(&relative).map_err(|e| e.to_string())?)
        .map_err(|e| e.to_string())?;
    std::fs::rename(temporary, directory.join("default-map.json")).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn empty_map() -> skate_data::skate_map::SkateMap {
        use skate_data::skate_map::*;
        SkateMap{version:14,name:"MegaPark".into(),spawn:[0.;3],heading:0.,environment:vec![],materials:vec![],textures:vec![],
            geometry:Geometry{vertices:vec![],indices:vec![],collision:vec![]},rails:vec![],doors:vec![],lights:vec![],routes:vec![],extensions:vec![]}
    }
    #[test]
    fn park_catalog_rejects_wrong_version_paths_base_and_supplement_fingerprints() {
        let root=std::env::temp_dir().join(format!("skate-park-catalog-{}",std::process::id()));
        let assets=root.join("assets"); let packs=assets.join("private/park-layouts");
        std::fs::create_dir_all(&packs).unwrap();
        let base=root.join("MegaPark.skate");std::fs::write(&base,b"base").unwrap();
        let fp=crate::config::map_fingerprint(Some(&base)).unwrap();
        assert_eq!(attach_map(&assets,&base,empty_map()).unwrap().1,fp);
        let catalog=packs.join("catalog.json");
        std::fs::write(&catalog,br#"{"version":2,"maps":{}}"#).unwrap();
        assert!(attach_map(&assets,&base,empty_map()).unwrap_err().contains("version"));
        for (file,base_fp,extra_fp) in [("../MegaPark.skate",fp,0),("MegaPark.skate",0,0),("MegaPark.skate",fp,0)] {
            std::fs::write(packs.join("MegaPark.skate"),b"broken").unwrap();
            let j=serde_json::json!({"version":1,"maps":{"MegaPark":{"file":file,"base_fingerprint":base_fp,"supplement_fingerprint":extra_fp}}});
            std::fs::write(&catalog,j.to_string()).unwrap();
            assert!(attach_map(&assets,&base,empty_map()).is_err());
        }
        let efp=crate::config::map_fingerprint(Some(&packs.join("MegaPark.skate"))).unwrap();
        let j=serde_json::json!({"version":1,"maps":{"MegaPark":{"file":"MegaPark.skate","base_fingerprint":fp,"supplement_fingerprint":efp}}});
        std::fs::write(&catalog,j.to_string()).unwrap();
        assert!(attach_map(&assets,&base,empty_map()).is_err());
        assert_eq!(std::fs::read(&base).unwrap(),b"base");
        for p in [&catalog,&base,&packs.join("MegaPark.skate")] {std::fs::remove_file(p).unwrap();}
        for p in [&packs,&assets.join("private"),&assets,&root] {std::fs::remove_dir(p).unwrap();}
    }
    #[test]
    fn default_pointer_round_trips_map_and_explicit_test_world() {
        let root = std::env::temp_dir().join(format!("skate-map-pointer-{}", std::process::id()));
        let assets = root.join("assets");
        let map = root.join("maps/example.skate");
        save_default(&assets, Some(&map)).unwrap();
        assert_eq!(default_map(&assets).unwrap(), Some(map));
        save_default(&assets, None).unwrap();
        assert_eq!(default_map(&assets).unwrap(), None);
        std::fs::write(root.join("settings/default-map.json"), br#""../outside.skate""#).unwrap();
        assert!(default_map(&assets).is_err());
        std::fs::remove_dir_all(root).unwrap();
    }
}
