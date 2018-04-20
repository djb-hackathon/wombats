/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef C_ARCORE_HELLOE_AR_PLANE_RENDERER_H_
#define C_ARCORE_HELLOE_AR_PLANE_RENDERER_H_

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/asset_manager.h>
#include <array>
#include <cstdint>
#include <cstdlib>
#include <string>
#include <vector>

#include "arcore_c_api.h"
#include "glm.h"

namespace hello_ar {

// PlaneRenderer renders ARCore plane type.
class PlaneRenderer {
 public:
  PlaneRenderer() = default;
  ~PlaneRenderer() = default;

  // Sets up OpenGL state used by the plane renderer.  Must be called on the
  // OpenGL thread.
  void InitializeGlContent(AAssetManager* asset_manager);

  // Draws the provided plane.
  void Draw(const glm::mat4& projection_mat, const glm::mat4& view_mat,
            const ArSession* ar_session, const ArPlane* ar_plane,
            const glm::vec3& color);

 private:
  void UpdateForPlane(const ArSession* ar_session, const ArPlane* ar_plane);

  std::vector<glm::vec3> vertices_;
  std::vector<GLushort> triangles_;
  glm::mat4 model_mat_ = glm::mat4(1.0f);

  GLuint vertex_buffers_[2];
  GLuint texture_id_;

  GLuint shader_program_;
  GLuint attri_vertices_;
  GLuint uniform_mvp_mat_;
  GLuint uniform_texture_;
  GLuint uniform_texture_mat_;
  GLuint uniform_color_;
};
}  // namespace hello_ar

#endif  // C_ARCORE_HELLOE_AR_PLANE_RENDERER_H_
