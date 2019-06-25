package aurora

trait PersistenceAdapter[A] {
  def get(id: String) : Option[A]
  def put(id: String, v: A): Unit
  def remove(id: String): Unit
}