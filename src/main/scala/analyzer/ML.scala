package analyzer

import smile.math.Math
import scala.collection.JavaConverters._

object ML {
  case class Attribute(`type`: Int)

  object Attribute {
    val Nominal: Int = smile.data.Attribute.Type.NOMINAL.ordinal
    val Numeric: Int = smile.data.Attribute.Type.NUMERIC.ordinal

    def apply(origin: smile.data.Attribute): Attribute = {
      new Attribute(origin.getType.ordinal)
    }
  }

  case class DecisionTreeNode(posteriori: IndexedSeq[Double],
                              splitFeature: Int,
                              splitValue: Double,
                              trueChild: Option[DecisionTreeNode],
                              falseChild: Option[DecisionTreeNode]) {
    def predict(attributes: IndexedSeq[Attribute], x: Array[Double]): IndexedSeq[Double] = {
      if (trueChild.isEmpty && falseChild.isEmpty) {
        posteriori
      } else {
        val attrType = attributes(splitFeature).`type`
        if (attrType == Attribute.Nominal) {
          if (x(splitFeature) == splitValue) {
            trueChild.get.predict(attributes, x)
          } else {
            falseChild.get.predict(attributes, x)
          }
        } else if (attrType == Attribute.Numeric) {
          if (x(splitFeature) <= splitValue) {
            trueChild.get.predict(attributes, x)
          } else {
            falseChild.get.predict(attributes, x)
          }
        } else {
          throw new IllegalStateException("Unsupported attribute type: " + attrType)
        }
      }
    }
  }

  object DecisionTreeNode {
    def apply(origin: AnyRef): DecisionTreeNode = {
      val originClass = origin.getClass

      val posterioriField = originClass.getDeclaredField("posteriori")
      posterioriField.setAccessible(true)
      val posteriori = posterioriField.get(origin).asInstanceOf[Array[Double]]

      val splitFeatureField = originClass.getDeclaredField("splitFeature")
      splitFeatureField.setAccessible(true)
      val splitFeature = splitFeatureField.get(origin).asInstanceOf[Int]

      val splitValueField = originClass.getDeclaredField("splitValue")
      splitValueField.setAccessible(true)
      val splitValue = splitValueField.get(origin).asInstanceOf[Double]

      val trueChildField = originClass.getDeclaredField("trueChild")
      trueChildField.setAccessible(true)
      val trueChildRef = trueChildField.get(origin)
      val trueChild =
        if (trueChildRef != null) Some(DecisionTreeNode(trueChildRef)) else None

      val falseChildField = originClass.getDeclaredField("falseChild")
      falseChildField.setAccessible(true)
      val falseChildRef = falseChildField.get(origin)
      val falseChild =
        if (falseChildRef != null) Some(DecisionTreeNode(falseChildRef)) else None

      new DecisionTreeNode(posteriori, splitFeature, splitValue, trueChild, falseChild)
    }
  }

  case class DecisionTree(attributes: IndexedSeq[Attribute], root: DecisionTreeNode) {
    def predict(x: Array[Double]): IndexedSeq[Double] = {
      root.predict(attributes, x)
    }
  }

  object DecisionTree {
    def apply(origin: smile.classification.DecisionTree): DecisionTree = {
      val originClass = origin.getClass

      val attributesField = originClass.getDeclaredField("attributes")
      attributesField.setAccessible(true)
      val attributesRef = attributesField.get(origin).asInstanceOf[Array[smile.data.Attribute]]
      val attributes = for (attribute <- attributesRef) yield Attribute(attribute)

      val rootField = originClass.getDeclaredField("root")
      rootField.setAccessible(true)
      val root = DecisionTreeNode(rootField.get(origin))

      new DecisionTree(attributes, root)
    }
  }


  case class RandomForestTree(tree: DecisionTree, weight: Double)

  object RandomForestTree {
    def apply(origin: AnyRef): RandomForestTree = {
      val originClass = origin.getClass

      val treeField = originClass.getDeclaredField("tree")
      treeField.setAccessible(true)
      val tree = DecisionTree(treeField.get(origin).asInstanceOf[smile.classification.DecisionTree])

      val weightField = originClass.getDeclaredField("weight")
      weightField.setAccessible(true)
      val weight = weightField.get(origin).asInstanceOf[Double]

      new RandomForestTree(tree, weight)
    }
  }


  case class RandomForest(trees: List[RandomForestTree], k: Int) {
    def predict(x: Array[Double]): Array[Double] = {
      val posteriori = Array.fill(k)(0.0)

      for (tree <- trees) {
        val pos = tree.tree.predict(x)
        for (i <- 0 until k) {
          posteriori(i) += tree.weight * pos(i)
        }
      }

      Math.unitize1(posteriori)
      posteriori
    }
  }

  object RandomForest {
    def apply(origin: smile.classification.RandomForest): RandomForest = {
      val originClass = origin.getClass

      val treesField = originClass.getDeclaredField("trees")
      treesField.setAccessible(true)
      val treesRef = treesField.get(origin).asInstanceOf[java.util.List[AnyRef]].asScala.toList
      val trees = for (tree <- treesRef) yield RandomForestTree(tree)

      val kField = originClass.getDeclaredField("k")
      kField.setAccessible(true)
      val k = kField.get(origin).asInstanceOf[Int]

      new RandomForest(trees, k)
    }
  }
}
