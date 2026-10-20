"""Offline blockymodel renderer seeded from resolved authored IconProperties.

Run with Blender: blender -b --python tools/Render-InventoryIcon.py -- model texture output properties.json
This does not modify game assets or invoke a Hytale server/client.
"""
import json
import math
import sys

import bpy
from mathutils import Euler, Matrix, Quaternion, Vector


arguments = sys.argv[sys.argv.index("--") + 1:]
model_path, texture_path, output_path = arguments[:3]
with open(arguments[3], encoding="utf-8") as handle:
    properties = json.load(handle)
with open(model_path, encoding="utf-8") as handle:
    model = json.load(handle)

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
texture = bpy.data.images.load(texture_path)
material = bpy.data.materials.new("Hytale item texture")
material.use_nodes = True
nodes = material.node_tree.nodes
image = nodes.new("ShaderNodeTexImage")
image.image = texture
image.interpolation = "Closest"
nodes.get("Principled BSDF").inputs["Base Color"].default_value = (1, 1, 1, 1)
nodes.get("Principled BSDF").inputs["Alpha"].default_value = 1
nodes.get("Principled BSDF").inputs["Emission Color"].default_value = (1, 1, 1, 1)
nodes.get("Principled BSDF").inputs["Emission Strength"].default_value = 1
nodes.get("Principled BSDF").inputs["Roughness"].default_value = 1


def coordinates(value, default=0):
    return Vector((value.get("x", default), value.get("y", default), value.get("z", default)))


def box(node, parent):
    shape = node["shape"]
    dimensions = coordinates(shape["settings"]["size"])
    stretch = coordinates(shape.get("stretch", {}), 1)
    half = Vector((dimensions.x * stretch.x, dimensions.y * stretch.y,
                   dimensions.z * stretch.z)) / 2
    ox, oy, oz = coordinates(shape.get("offset", {}))
    x0, x1 = ox - half.x, ox + half.x
    y0, y1 = oy - half.y, oy + half.y
    z0, z1 = oz - half.z, oz + half.z
    vertices = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    faces = {"front": (4, 5, 6, 7), "back": (1, 0, 3, 2),
             "top": (7, 6, 2, 3), "bottom": (0, 1, 5, 4),
             "left": (0, 4, 7, 3), "right": (5, 1, 2, 6)}
    mesh = bpy.data.meshes.new(node.get("name", "box"))
    mesh.from_pydata(vertices, [], list(faces.values()))
    mesh.update()
    uv_layer = mesh.uv_layers.new()
    layout = shape.get("textureLayout", {})
    for face, polygon in zip(faces, mesh.polygons):
        face_layout = layout.get(face, {})
        origin = face_layout.get("offset", {})
        width = dimensions.x if face in ("front", "back", "top", "bottom") else dimensions.z
        height = dimensions.y if face in ("front", "back", "left", "right") else dimensions.z
        u0, u1 = origin.get("x", 0) / texture.size[0], (origin.get("x", 0) + width) / texture.size[0]
        v0, v1 = 1 - origin.get("y", 0) / texture.size[1], 1 - (origin.get("y", 0) + height) / texture.size[1]
        corners = ((u0, v1), (u1, v1), (u1, v0), (u0, v0))
        for index, loop in enumerate(polygon.loop_indices):
            uv_layer.data[loop].uv = corners[index]
    obj = bpy.data.objects.new(node.get("name", "box"), mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(material)
    obj.matrix_world = parent
    return obj


objects = []


def walk(node, parent):
    position = coordinates(node.get("position", {}))
    q = node.get("orientation", {})
    quaternion = Quaternion((q.get("w", 1), q.get("x", 0), q.get("y", 0), q.get("z", 0)))
    matrix = parent @ Matrix.Translation(position) @ quaternion.to_matrix().to_4x4()
    if node.get("shape", {}).get("type") == "box" and "PLACEHOLDER" not in node.get("name", ""):
        objects.append(box(node, matrix))
    for child in node.get("children", []):
        walk(child, matrix)


for root in model["nodes"]:
    walk(root, Matrix.Identity(4))

if not objects:
    raise RuntimeError("No renderable model boxes")

bpy.context.view_layer.update()
points = [obj.matrix_world @ Vector(corner) for obj in objects for corner in obj.bound_box]
center = sum(points, Vector()) / len(points)
angles = properties["Rotation"]
scale = float(properties["Scale"])
if len(angles) != 3 or not math.isfinite(scale) or scale <= 0:
    raise ValueError("Authored IconProperties must have Rotation[3] and positive Scale")
rotation = Euler(tuple(math.radians(value) for value in angles), "XYZ").to_matrix().to_4x4()
native_seed = rotation @ Matrix.Scale(scale, 4)
for obj in objects:
    obj.matrix_world = native_seed @ Matrix.Translation(-center) @ obj.matrix_world
bpy.context.view_layer.update()
points = [obj.matrix_world @ Vector(corner) for obj in objects for corner in obj.bound_box]
width = max(p.x for p in points) - min(p.x for p in points)
height = max(p.y for p in points) - min(p.y for p in points)

bpy.ops.object.camera_add(location=(0, 0, 180))
camera = bpy.context.object
camera.rotation_euler = (-camera.location).to_track_quat("-Z", "Y").to_euler()
camera.data.type = "ORTHO"
camera.data.ortho_scale = max(width, height) * 1.12
bpy.context.scene.camera = camera
bpy.ops.object.light_add(type="AREA", location=(55, 90, 120))
bpy.context.object.data.energy = 700
bpy.context.object.data.shape = "DISK"
bpy.context.object.data.size = 90
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = 16
scene.render.resolution_x = 512
scene.render.resolution_y = 512
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.filepath = output_path
bpy.ops.render.render(write_still=True)


