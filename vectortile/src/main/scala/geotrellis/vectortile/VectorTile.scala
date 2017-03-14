/*
 * Copyright 2016 - 2017 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.vectortile

import geotrellis.vector.Extent
import geotrellis.vectortile.internal._
import geotrellis.vectortile.internal.{vector_tile => vt}

// --- //

/**
  * A concrete representation of a VectorTile, as one decoded from Protobuf
  * bytes. At its simplest, a Tile is just a collection of Layers. We opt
  * to expose each Layer name at the Tile level, as the keys of a [[Map]].
  * This way, if the layer names are known by the user ahead of time,
  * they can search through the Tile quickly.
  *
  * {{{
  * import geotrellis.vectortile._
  *
  * val bytes: Array[Byte] = ...  // from some `.mvt` file
  * val key: SpatialKey = ...  // preknown
  * val layout: LayoutDefinition = ...  // preknown
  * val tileExtent: Extent = layout.mapTransform(key)
  *
  * val tile: VectorTile = VectorTile.fromBytes(bytes, tileExtent)
  * }}}
  *
  * @constructor This is not meant to be called directly - see this class's
  * companion object for the available helper methods.
  */
case class VectorTile(layers: Map[String, Layer], tileExtent: Extent) {
  /** Encode this VectorTile back into a mid-level Protobuf object. */
  private def toProtobuf: vt.Tile = vt.Tile(layers = layers.values.map(_.toProtobuf).toSeq)

  /** Encode this VectorTile back into its original form of Protobuf bytes. */
  def toBytes: Array[Byte] = toProtobuf.toByteArray
}

object VectorTile {
  /** Create a VectorTile from a low-level protobuf Tile type. */
  private def fromPBTile(tile: vt.Tile, tileExtent: Extent): VectorTile = {

    val layers: Map[String, Layer] = tile.layers.map({ l =>
      val pbl = LazyLayer(l, tileExtent)

      pbl.name -> pbl
    }).toMap

    VectorTile(layers, tileExtent)
  }

  /** Create a [[VectorTile]] from raw Protobuf bytes.
    *
    * @param bytes  Raw Protobuf bytes from a `.mvt` file or otherwise.
    * @param tileExtent The [[Extent]] of this tile, '''not''' the global extent.
    */
  def fromBytes(bytes: Array[Byte], tileExtent: Extent): VectorTile =
    fromPBTile(vt.Tile.parseFrom(bytes), tileExtent)

}
