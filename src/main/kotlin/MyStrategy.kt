import model.*

class MyStrategy {

    private fun getNearestEnemy(unit: model.Unit, game: Game): model.Unit? {
        var nearestEnemy: model.Unit? = null
        for (other in game.units) {
            if (other.playerId != unit.playerId) {
                if (nearestEnemy == null ||
                        distanceSqr(unit.position, other.position) < distanceSqr(unit.position, nearestEnemy.position)) {
                    nearestEnemy = other
                }
            }
        }
        return nearestEnemy
    }

    private fun getNearestWeapon(unit: model.Unit, game: Game)
            = getNearestLootBox(unit, game, Item.Weapon::class.java)

    private fun getNearestHealthPack(unit: model.Unit, game: Game)
            = getNearestLootBox(unit, game, Item.HealthPack::class.java)

    private fun getNearestLootBox(unit: model.Unit, game: Game, itemClass: Class<out Item>): LootBox? {
        var nearestItem: LootBox? = null

        for (lootBox in game.lootBoxes) {
            if (itemClass.isInstance(lootBox.item)) {
                if (nearestItem == null ||
                        distanceSqr(unit.position, lootBox.position) < distanceSqr(unit.position, nearestItem.position)) {
                    nearestItem = lootBox
                }
            }
        }

        return nearestItem
    }

    private fun model.Unit.hasWeapon() = weapon != null
    private fun model.Unit.tookDamage(game: Game) = health < game.properties.unitMaxHealth

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        val nearestEnemy = getNearestEnemy(unit, game)
        val nearestWeapon = getNearestWeapon(unit, game)
        val nearestHealthPack = getNearestHealthPack(unit, game)

        // I don't need to move, and better stay in the health pack
        var targetPos: Vec2Double = unit.position

        if (!unit.hasWeapon() && nearestWeapon != null) {
            targetPos = nearestWeapon.position
            // println("Target Weapon: ${targetPos.x}, ${targetPos.y}")
        } else if (nearestHealthPack != null /*&& unit.tookDamage(game)*/) {
            targetPos = nearestHealthPack.position
            // println("Target HealthPack: ${targetPos.x}, ${targetPos.y}")
        }/*else if (nearestEnemy != null) {
            targetPos = nearestEnemy.position
            // println("Target Enemy: ${targetPos.x}, ${targetPos.y}")
        }*/
        // println("Target pos: ${targetPos.x}, ${targetPos.y}")
        // println("Unit pos: ${unit.position.x}, ${unit.position.y}")
        // debug.draw(CustomData.Log("Target pos: $targetPos"))


        var aim = Vec2Double(0.0, 0.0)
        if (nearestEnemy != null) {
            aim = Vec2Double(
                nearestEnemy.position.x - unit.position.x,
                nearestEnemy.position.y - unit.position.y
            )
        }


        var jump = targetPos.y > unit.position.y
        if (targetPos.x > unit.position.x &&
                game.level.tiles[(unit.position.x + 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
            jump = true
        }
        if (targetPos.x < unit.position.x
                && game.level.tiles[(unit.position.x - 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
            jump = true
        }

        val action = UnitAction()
        action.velocity = targetPos.x - unit.position.x
        action.jump = jump
        action.jumpDown = !jump
        action.aim = aim
        action.shoot = true
        action.reload = false
        action.swapWeapon = false
        action.plantMine = false
        return action
    }

    companion object {
        internal fun distanceSqr(a: Vec2Double, b: Vec2Double): Double {
            return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        }
    }
}
