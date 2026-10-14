use super::*;

fn remap_definition(bytes: &mut [u8], texture_offset: u32) -> Result<(), String> {
    let mut r=Reader{bytes,at:0}; r.take(16)?; r.string()?; r.take(8)?;
    let count=r.u()?; r.check_count(count,20)?;
    let mut edits=Vec::new();
    for _ in 0..count {
        r.string()?; let at=r.at; let id=r.u()?;
        if id!=0 { edits.push((at,id.checked_add(texture_offset).ok_or("Supplement texture overflow")?)); }
        r.take(12)?;
    }
    for (at,id) in edits {bytes[at..at+4].copy_from_slice(&id.to_le_bytes());}
    Ok(())
}

fn archive_records(bytes: &[u8]) -> Result<u32,String> {
    if bytes.len()<12 || &bytes[..8]!=b"RWCMSET1" {return Err("Invalid supplement collision archive".into());}
    crate::retail_collision::visit_clusters(bytes,|_,_|Ok(()))?;
    Ok(u32::from_le_bytes(bytes[8..12].try_into().unwrap()))
}

fn merge_metadata(map:&mut SkateMap,extension:Extension,extra_rails:usize) -> Result<(),String> {
    use serde_json::{Value,json};
    let mut source:Value=serde_json::from_slice(&extension.payload).map_err(|e|format!("Supplement WMET: {e}"))?;
    if source["grind_coordinate_policy"]["mode"].as_str()!=Some("world_space") {
        return Err("Supplement rails require world-space provenance".into());
    }
    let records=source.as_object_mut().ok_or("Supplement WMET is not an object")?
        .remove("grind_splines").ok_or("Supplement WMET rail table missing")?;
    let records=records.as_array().ok_or("Supplement WMET rail table is not an array")?;
    if records.len()!=extra_rails {return Err("Supplement WMET/package rail count mismatch".into());}
    let indices:Vec<_>=map.extensions.iter().enumerate().filter(|(_,e)|e.tag==*b"WMET").map(|(i,_)|i).collect();
    if indices.len()>1 {return Err("Multiple base WMET records".into());}
    let (mut metadata,index)=if let Some(&i)=indices.first() {
        (serde_json::from_slice::<Value>(&map.extensions[i].payload).map_err(|e|format!("Base WMET: {e}"))?,Some(i))
    } else {(json!({"grind_coordinate_policy":{"mode":"world_space"},"grind_splines":[]}),None)};
    if metadata["grind_coordinate_policy"]["mode"].as_str()!=Some("world_space") {return Err("Base rails are not in world space".into());}
    let table=metadata.get_mut("grind_splines").and_then(Value::as_array_mut).ok_or("Base WMET rail table missing")?;
    if table.len()!=map.rails.len() {return Err("Base WMET/package rail count mismatch".into());}
    table.extend(records.iter().cloned());
    let object=metadata.as_object_mut().ok_or("Base WMET is not an object")?;
    let layers=object.entry("park_layers").or_insert_with(||json!([])).as_array_mut().ok_or("Invalid park source metadata")?;
    layers.push(source);
    let payload=serde_json::to_vec(&metadata).map_err(|e|e.to_string())?;
    if let Some(i)=index {map.extensions[i].payload=payload;}
    else {map.extensions.push(Extension{tag:*b"WMET",schema:1,payload});}
    Ok(())
}

impl SkateMap {
    /// Compose a baked park with its district. The caller only publishes the
    /// returned complete map, so a failed supplement cannot replace live state.
    pub fn with_supplement(mut self, mut extra: Self) -> Result<Self,String> {
        if self.name!=extra.name || !extra.doors.is_empty() || !extra.lights.is_empty() || !extra.routes.is_empty()
            || extra.extensions.iter().any(|e| !matches!(&e.tag,b"RWCM"|b"WMET") || e.schema!=1) {
            return Err("Supplement belongs to another map or contains unsupported content".into());
        }
        let texture_offset=u32::try_from(self.textures.len()).map_err(|_|"Texture count overflow")?;
        let vertex_offset=u32::try_from(self.geometry.vertices.len()).map_err(|_|"Vertex count overflow")?;
        u32::try_from(self.geometry.vertices.len()+extra.geometry.vertices.len()).map_err(|_|"Merged vertex count overflow")?;
        let mut material_ids=Vec::with_capacity(extra.materials.len());
        for mut m in extra.materials.drain(..) {
            for id in &mut m.textures {if *id!=0 {*id=id.checked_add(texture_offset).ok_or("Texture index overflow")?;}}
            if let Some(b)=&mut m.retail_definition {remap_definition(b,texture_offset)?;}
            // Exported instances use stable source material names. Identical
            // surfaces can share batches instead of generating a draw per piece.
            let id=if let Some(i)=self.materials.iter().position(|candidate|candidate==&m) {i+1}
                else {self.materials.push(m);self.materials.len()};
            material_ids.push(u32::try_from(id).map_err(|_|"Material count overflow")?);
        }
        let remap_material=|id: &mut u32| -> Result<(),String> {
            if *id!=0 {*id=*material_ids.get((*id-1) as usize).ok_or("Invalid supplement material reference")?;}
            Ok(())
        };
        for v in &mut extra.geometry.vertices {remap_material(&mut v.material)?;}
        for c in &mut extra.geometry.collision {remap_material(&mut c.material)?;}
        for id in &mut extra.geometry.indices {*id=id.checked_add(vertex_offset).ok_or("Vertex index overflow")?;}
        for extension in extra.extensions {
            if extension.tag==*b"WMET" {merge_metadata(&mut self,extension,extra.rails.len())?;continue;}
            if extension.tag!=*b"RWCM" {continue;}
            let count=archive_records(&extension.payload)?;
            if let Some(existing)=self.extensions.iter_mut().find(|e|e.tag==*b"RWCM") {
                let old=archive_records(&existing.payload)?;
                let total=old.checked_add(count).ok_or("Collision archive count overflow")?;
                existing.payload[8..12].copy_from_slice(&total.to_le_bytes());
                existing.payload.extend_from_slice(&extension.payload[12..]);
            } else {self.extensions.push(extension);}
        }
        self.textures.append(&mut extra.textures);
        self.geometry.vertices.append(&mut extra.geometry.vertices);
        self.geometry.indices.append(&mut extra.geometry.indices);
        self.geometry.collision.append(&mut extra.geometry.collision);
        self.rails.append(&mut extra.rails);
        self.spawn=extra.spawn;self.heading=extra.heading;
        Ok(self)
    }
}
