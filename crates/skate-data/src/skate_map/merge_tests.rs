use super::*;

fn definition(texture: u32) -> Vec<u8> {
    let mut b=vec![0;16];
    let s=b"environment.default"; b.extend((s.len() as u32).to_le_bytes()); b.extend(s);
    b.extend(1u32.to_le_bytes()); b.extend(0u32.to_le_bytes()); b.extend(1u32.to_le_bytes());
    b.extend(7u32.to_le_bytes()); b.extend(b"diffuse"); b.extend(texture.to_le_bytes());
    b.extend([0;12]); b.extend(0u32.to_le_bytes()); b.extend(0u32.to_le_bytes()); b
}
fn material() -> Material {
    Material{name:"shared".into(),flags:1,friction:0.8,restitution:0.,color:[1.;3],roughness:0.5,emissive:0.,
        textures:[1,0,0,0,0],indirect_strength:0.,alpha_mode:0,alpha_cutoff:0.5,audio:3,physics:1,pattern:0,
        depth_layer:Some(0),retail_definition:Some(definition(1))}
}
fn map() -> SkateMap {
    SkateMap{version:14,name:"MegaPark".into(),spawn:[0.;3],heading:0.,environment:vec![1.;45],
        materials:vec![material()],textures:vec![Texture{name:"t".into(),width:1,height:1,color_space:1,rgba:vec![255;4]}],
        geometry:Geometry{vertices:vec![Vertex{position:[0.;3],normal:[0.,1.,0.],uv:[0.;2],lightmap_uv:[0.;2],material:1,decal_uv:None,tangent_frame:None}],
            indices:vec![0,0,0],collision:vec![]},rails:vec![],doors:vec![],lights:vec![],routes:vec![],extensions:vec![]}
}
#[test]
fn supplements_remap_texture_bindings_and_deduplicate_repeated_materials() {
    let base=map(); let mut extra=map(); extra.spawn=[230.,0.48,-73.]; extra.heading=-1.57;
    extra.materials.push(material()); let mut v=extra.geometry.vertices[0].clone(); v.material=2; extra.geometry.vertices.push(v);
    let merged=base.with_supplement(extra).unwrap();
    assert_eq!(merged.spawn,[230.,0.48,-73.]); assert_eq!(merged.environment,vec![1.;45]);
    assert_eq!(merged.textures.len(),2); assert_eq!(merged.materials.len(),2);
    assert_eq!(merged.geometry.indices,vec![0,0,0,1,1,1]);
    assert_eq!(merged.geometry.vertices[1].material,2); assert_eq!(merged.geometry.vertices[2].material,2);
    assert_eq!(merged.materials[1].textures,[2,0,0,0,0]);
    assert_eq!(merged.materials[1].retail_definition.as_ref().unwrap(),&definition(2));
}
#[test]
fn a_supplement_from_another_map_or_unknown_extension_is_rejected() {
    let mut extra=map(); extra.name="University".into(); assert!(map().with_supplement(extra).is_err());
    let mut extra=map(); extra.extensions.push(Extension{tag:*b"NOPE",schema:1,payload:vec![]});
    assert!(map().with_supplement(extra).is_err());
}

#[test]
fn supplemental_rails_keep_one_complete_world_space_provenance_table() {
    let rail=||Rail{name:"rail".into(),closed:false,points:vec![[0.;3],[1.;3]],native:None};
    let metadata=|label:&str|Extension{tag:*b"WMET",schema:1,payload:serde_json::to_vec(&serde_json::json!({
        "scope":label,"grind_coordinate_policy":{"mode":"world_space"},"grind_splines":[{"source":label}]
    })).unwrap()};
    let mut base=map();base.rails.push(rail());base.extensions.push(metadata("base"));
    let mut extra=map();extra.rails.push(rail());extra.extensions.push(metadata("park"));
    let merged=base.with_supplement(extra).unwrap();
    let entries:Vec<_>=merged.extensions.iter().filter(|e|e.tag==*b"WMET").collect();
    assert_eq!(entries.len(),1);
    let j:serde_json::Value=serde_json::from_slice(&entries[0].payload).unwrap();
    assert_eq!(j["grind_splines"].as_array().unwrap().len(),merged.rails.len());
    assert_eq!(j["grind_splines"][1]["source"],"park");
    assert_eq!(j["scope"],"base");
}
