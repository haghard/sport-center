package ddd

object Shard {

  def shardOf[T <: BusinessEntity: BusinessEntityActorFactory: ShardResolution: ShardFactory] = {
    implicitly[ShardFactory[T]].getOrCreate
  }
}
