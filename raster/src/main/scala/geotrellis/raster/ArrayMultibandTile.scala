package geotrellis.raster

import geotrellis.raster.summary._
import geotrellis.vector.Extent

import spire.syntax.cfor._


/**
  * The companion object for the [[ArrayMultibandTile]] type.
  */
object ArrayMultibandTile {

  /**
    * Create a new [[ArrayMultibandTile]] from a given bunch of
    * [[Tile]] objects.
    *
    * @param   bands  The sequence of source Tile objects.
    * @return         An ArrayMultibandTile whose bands are given tiles.
    */
  def apply(bands: Tile*): ArrayMultibandTile =
    apply(bands.toArray)

  /**
    * Create a new [[ArrayMultibandTile]] from a given [[Traversable]]
    * of [[Tile]] objects.
    *
    * @param   bands  The Traversable collection of source Tile objects.
    * @return         An ArrayMultibandTile whose bands are given tiles.
    */
  def apply(bands: Traversable[Tile]): ArrayMultibandTile =
    new ArrayMultibandTile(bands.toArray)

  /**
    * Create a new [[ArrayMultibandTile]] from a given Array of
    * [[Tile]] objects.
    *
    * @param   bands  The array of source Tile objects.
    * @return         An ArrayMultibandTile whose bands are given tiles.
    */
  def apply(bands: Array[Tile]): ArrayMultibandTile =
    new ArrayMultibandTile(bands)

  /**
    * "Allocate" a new [[ArrayMultibandTile]].  Arrays for all of the
    * bands are allocated, then fused into an ArrayMultibandTile.
    *
    * @param   bands  The number of bands that the new ArrayMultibandTile should have.
    * @param   cols   The number of columns that the new ArrayMultibandTile should have.
    * @param   rows   The number of rows that the new ArrayMultibandTile should have.
    * @return         The new ArrayMultibandTile.
    */
  def alloc(t: CellType, bands: Int, cols: Int, rows: Int): ArrayMultibandTile = {
    ArrayMultibandTile(for (_ <- 0 until bands) yield ArrayTile.alloc(t, cols, rows))
  }

  /**
    * Create an empty [[ArrayMultibandTile]].  Arrays for all of the
    * bands are allocated, then fused into an ArrayMultibandTile.
    *
    * @param   bands  The number of bands that the new ArrayMultibandTile should have.
    * @param   cols   The number of columns that the new ArrayMultibandTile should have.
    * @param   rows   The number of rows that the new ArrayMultibandTile should have.
    * @return         The new ArrayMultibandTile.
    */
  def empty(t: CellType, bands: Int, cols: Int, rows: Int): ArrayMultibandTile = {
    ArrayMultibandTile(for (_ <- 0 until bands) yield ArrayTile.empty(t, cols, rows))
  }
}

/**
  * The [[ArrayMultibandTile]] type.
  */
class ArrayMultibandTile(private val _bands: Array[Tile]) extends MultibandTile with MacroMultibandCombiners {
  val bandCount = _bands.size

  assert(bandCount > 0, "Band count must be greater than 0")
  private def validateBand(i: Int) = assert(i < bandCount, s"Band index out of bounds. Band Count: $bandCount Requested Band Index: $i")

  val cellType = _bands(0).cellType
  val cols: Int = _bands(0).cols
  val rows: Int = _bands(0).rows

  // Check all bands for consistency.
  cfor(0)(_ < bandCount, _ + 1) { i =>
    assert(_bands(i).cellType == cellType, s"Band $i cell type does not match, ${_bands(i).cellType} != $cellType")
    assert(_bands(i).cols == cols, s"Band $i cols does not match, ${_bands(i).cols} != $cols")
    assert(_bands(i).rows == rows, s"Band $i rows does not match, ${_bands(i).rows} != $rows")
  }

  /**
    * Retrieve one band of an [[ArrayMultibandTile]].
    *
    * @param   bandIndex  The index of the band to be retrieved
    * @return             A (single-band) Tile containing the requested band.
    */
  def band(bandIndex: Int): Tile = {
    if(bandIndex >= bandCount) { throw new IllegalArgumentException(s"Band $bandIndex does not exist") }
    _bands(bandIndex)
  }

  def bands = _bands.toVector

  /**
    * Convert the present [[ArrayMultibandTile]] into a
    * [[MultibandTile]] with the given CellType.
    *
    * @param   newCellType  The destination CellType
    * @return               A MultibandTile of the given CellType
    */
  def convert(newCellType: CellType): MultibandTile = {
    val newBands = Array.ofDim[Tile](bandCount)
    cfor(0)(_ < bandCount, _ + 1) { i =>
      newBands(i) = band(i).convert(newCellType)
    }

    ArrayMultibandTile(newBands)
  }

  /**
    * Map over a subset of the bands of an [[ArrayMultibandTile]] to
    * create a new integer-valued [[MultibandTile]].
    *
    * @param   subset  A sequence containing the subset of bands that are of interest.
    * @param   f       A function to map over the bands.
    * @return          A MultibandTile containing the result
    */
  def map(subset: Seq[Int])(f: (Int, Int) => Int): MultibandTile = {
    val newBands = Array.ofDim[Tile](bandCount)
    val set = subset.toSet

    subset.foreach({ b =>
      require(0 <= b && b < bandCount, "All elements of subset must be present")
    })

    (0 until bandCount).foreach({ b =>
      if (set.contains(b))
        newBands(b) = band(b).map({ z => f(b, z) })
      else if (cellType.isFloatingPoint)
        newBands(b) = band(b).map({ z => z })
      else
        newBands(b) = band(b)
    })

    ArrayMultibandTile(newBands)
  }

  /**
    * Map over a subset of the bands of an [[ArrayMultibandTile]] to
    * create a new double-valued [[MultibandTile]] tile.
    *
    * @param   subset  A sequence containing the subset of bands that are of interest.
    * @param   f       A function to map over the bands.
    * @return          A MultibandTile containing the result
    */
  def mapDouble(subset: Seq[Int])(f: (Int, Double) => Double): MultibandTile = {
    val newBands = Array.ofDim[Tile](bandCount)
    val set = subset.toSet

    subset.foreach({ b =>
      require(0 <= b && b < bandCount, "All elements of subset must be present")
    })

    (0 until bandCount).foreach({ b =>
      if (set.contains(b))
        newBands(b) = band(b).mapDouble({ z => f(b, z) })
      else if (cellType.isFloatingPoint)
        newBands(b) = band(b)
      else
        newBands(b) = band(b).mapDouble({ z => z })
    })

    ArrayMultibandTile(newBands)
  }

  /**
    * Map each band's int value.
    *
    * @param   f  Function that takes in a band number and a value, and returns the mapped value for that cell value.
    * @return     A [[MultibandTile]] containing the result
    */
  def map(f: (Int, Int) => Int): MultibandTile = {
    val newBands = Array.ofDim[Tile](bandCount)
    cfor(0)(_ < bandCount, _ + 1) { i =>
      newBands(i) = band(i).map { z => f(i, z) }
    }

    ArrayMultibandTile(newBands)
  }

  /**
    * Map each band's double value.
    *
    * @param   f  Function that takes in a band number and a value, and returns the mapped value for that cell value.
    * @return     A [[MultibandTile]] containing the result
    */
  def mapDouble(f: (Int, Double) => Double): MultibandTile = {
    val newBands = Array.ofDim[Tile](bandCount)
    cfor(0)(_ < bandCount, _ + 1) { i =>
      newBands(i) = band(i).mapDouble { z => f(i, z) }
    }

    ArrayMultibandTile(newBands)
  }

  /**
    * Map a single band's int value.
    *
    * @param   bandIndex  Band index to map over.
    * @param   f          Function that takes in a band number and a value, and returns the mapped value for that cell value.
    * @return             A [[MultibandTile]] containing the result
    */
  def map(b0: Int)(f: Int => Int): MultibandTile = {
    validateBand(b0)
    val newBands = _bands.clone
    newBands(b0) = band(b0) map f

    ArrayMultibandTile(newBands)
  }

  /**
    * Map each band's double value.
    *
    * @param   f  Function that takes in a band number and a value, and returns the mapped value for that cell value.
    * @return     A [[MultibandTile]] containing the result
    */
  def mapDouble(b0: Int)(f: Double => Double): MultibandTile = {
    validateBand(b0)
    val newBands = _bands.clone
    newBands(b0) = band(b0) mapDouble f

    ArrayMultibandTile(newBands)
  }

  /**
    * Iterate over each band's int value.
    *
    * @param  f  Function that takes in a band number and a value, and produces some side-effect.
    */
  def foreach(f: (Int, Int) => Unit): Unit = {
    cfor(0)(_ < bandCount, _ + 1) { i =>
      band(i).foreach { z => f(i, z) }
    }
  }

  /**
    * Iterate over each band's double value.
    *
    * @param  f  Function that takes in a band number and a value, and produces some side-effect.
    */
  def foreachDouble(f: (Int, Double) => Unit): Unit = {
    cfor(0)(_ < bandCount, _ + 1) { i =>
      band(i).foreachDouble { z => f(i, z) }
    }
  }

  /**
    * Iterate over a single band's int value.
    *
    * @param  bandIndex  Band index to foreach over.
    * @param  f          Function that takes in a band number and a value, and and produces some side-effect.
    */
  def foreach(b0: Int)(f: Int => Unit): Unit = {
    validateBand(b0)
    band(b0) foreach f
  }

  /**
    * Iterate over a single band's double value.
    *
    * @param    bandIndex  Band index to foreach over.
    * @param    f          Function that takes in a band number and a value, and produces some side-effect.
    */
  def foreachDouble(b0: Int)(f: Double => Unit): Unit = {
    validateBand(b0)
    band(b0) foreachDouble f
  }

  /**
    * Combine a subset of the bands of a [[ArrayMultibandTile]] into a
    * new integer-valued [[MultibandTile]] using the function f.
    *
    * @param    subset   A sequence containing the subset of bands that are of interest.
    * @param    f        A function to combine the bands.
    * @return            The [[Tile]] that results from combining the bands.
    */
  def combine(subset: Seq[Int])(f: Seq[Int] => Int): Tile = {
    subset.foreach({ b => require(0 <= b && b < bandCount, "All elements of subset must be present") })

    val result = ArrayTile.empty(cellType, cols, rows)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        val data = subset.map({ b => band(b).get(col, row) })
        result.set(col, row, f(data))
      }
    }
    result
  }

  /**
    * Combine a subset of the bands of a [[ArrayMultibandTile]] into a
    * new double-valued [[MultibandTile]] using the function f.
    *
    * @param    subset   A sequence containing the subset of bands that are of interest.
    * @param    f        A function to combine the bands.
    * @return            The [[Tile]] that results from combining the bands.
    */
  def combineDouble(subset: Seq[Int])(f: Seq[Double] => Double): Tile = {
    subset.foreach({ b => require(0 <= b && b < bandCount, "All elements of subset must be present") })

    val result = ArrayTile.empty(cellType, cols, rows)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        val data = subset.map({ b => band(b).getDouble(col, row) })
        result.setDouble(col, row, f(data))
      }
    }
    result
  }

  /**
    * Combine each int band value for each cell.  This method will be
    * inherently slower than calling a method with explicitly stated
    * bands, so if you have as many or fewer bands to combine than an
    * explicit method call, use that.
    *
    * @param   f  A function from Array[Int] to Int.  The array contains the values of each band at a particular point.
    * @return     The [[Tile]] that results from combining the bands.
    */
  def combine(f: Array[Int] => Int): Tile = {
    val result = ArrayTile.empty(cellType, cols, rows)
    val arr = Array.ofDim[Int](bandCount)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        cfor(0)(_ < bandCount, _ + 1) { b =>
          arr(b) = band(b).get(col, row)
        }
        result.set(col, row, f(arr))
      }
    }
    result
  }

  /**
    * Combine two int band value for each cell.
    *
    * @param   b0  The index of the first band to combine.
    * @param   b1  The index of the second band to combine.
    * @param   f   A function from (Int, Int) to Int.  The tuple contains the respective values of the two bands at a particular point.
    * @return      The [[Tile]] that results from combining the bands.
    */
  def combine(b0: Int, b1: Int)(f: (Int, Int) => Int): Tile = {
    val band1 = band(b0)
    val band2 = band(b1)
    val result = ArrayTile.empty(cellType, cols, rows)
    val arr = Array.ofDim[Int](bandCount)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        result.set(col, row, f(band1.get(col, row), band2.get(col, row)))
      }
    }
    result
  }

  /**
    * Combine each double band value for each cell.  This method will
    * be inherently slower than calling a method with explicitly
    * stated bands, so if you have as many or fewer bands to combine
    * than an explicit method call, use that.
    *
    * @param   f  A function from Array[Double] to Double.  The array contains the values of each band at a particular point.
    * @return     The [[Tile]] that results from combining the bands.
    */
  def combineDouble(f: Array[Double] => Double) = {
    val result = ArrayTile.empty(cellType, cols, rows)
    val arr = Array.ofDim[Double](bandCount)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        cfor(0)(_ < bandCount, _ + 1) { b =>
          arr(b) = band(b).getDouble(col, row)
        }
        result.setDouble(col, row, f(arr))
      }
    }
    result
  }

  /**
    * Combine two double band value for each cell.
    *
    * @param   b0  The index of the first band to combine.
    * @param   b1  The index of the second band to combine.
    * @param   f   A function from (Double, Double) to Double.  The tuple contains the respective values of the two bands at a particular point.
    * @return      The [[Tile]] that results from combining the bands.
    */
  def combineDouble(b0: Int, b1: Int)(f: (Double, Double) => Double): Tile = {
    val band1 = band(b0)
    val band2 = band(b1)
    val result = ArrayTile.empty(cellType, cols, rows)
    val arr = Array.ofDim[Int](bandCount)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        result.setDouble(col, row, f(band1.getDouble(col, row), band2.getDouble(col, row)))
      }
    }
    result
  }

  /**
    * Produce a new [[ArrayMultibandTile]] whose bands are taken from
    * the source ArrayMultibandTile according to the bandSequence.
    * For example, if the bandSequence is List(1,7), then the new
    * ArrayMultibandTile will have two bands, the eighth and second
    * from the source ArrayMultibandTile.
    *
    * @param  bandSequence  The list of indices to use to create the new ArrayMultibandTile.
    * @return               The resulting ArrayMultibandTile.
    */
  def subsetBands(bandSequence: Seq[Int]): ArrayMultibandTile = {
    val newBands = Array.ofDim[Tile](bandSequence.size)
    var i = 0

    require(bandSequence.size <= bandCount)
    bandSequence.foreach({ j =>
      newBands(i) = band(j)
      i += 1
    })

    new ArrayMultibandTile(newBands)
  }

  override def equals(other: Any) = {
    if (other == null) {
      false
    } else other match {
      case that : ArrayMultibandTile =>
        case class NotEqualException() extends Exception()
        var result = true
        try {
          for (b <- 0 until bandCount) {
            if (band(b) != that.band(b)) throw NotEqualException()
          }
        } catch {
          case e : NotEqualException =>
            result = false
        }
        result
      case _ =>
        false
    }
  }
}
