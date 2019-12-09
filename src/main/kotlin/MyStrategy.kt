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
    private fun model.Unit.hasWeapon(weaponType: WeaponType)
            = weapon?.typ == weaponType
    private fun model.Unit.tookDamage(game: Game) = health < game.properties.unitMaxHealth

    private fun model.Unit.aimTo(enemy: model.Unit?): Vec2Double {
        var aim = Vec2Double(0.0, 0.0)
        if (enemy == null) return aim

        aim = Vec2Double(
            enemy.position.x - position.x,
            enemy.position.y - position.y
        )

        return aim
    }

    private fun model.Unit.shouldSwapWeapon(nearestWeaponType: WeaponType): Boolean {
        if (weapon == null)
            return true

        // if the nearestWeapon is a Rifle, and unit doesn't have one, better swap
        if (nearestWeaponType == WeaponType.ASSAULT_RIFLE && weapon?.typ != WeaponType.ASSAULT_RIFLE)
            return true

        return false
    }

    private fun model.Unit.shouldShoot(nearestEnemy: model.Unit?, aim: Vec2Double, game: Game): Boolean {
        if (nearestEnemy == null)
            return false

        // don't shoot if the direction is blocked by a wall
        if (aim.x > 0 && game.nextTileRight(this) == Tile.WALL)
            return false
        if (aim.x < 0 && game.nextTileLeft(this) == Tile.WALL)
            return false

        // don't shoot a Rocket if there is a wall near
        val canExplodeNearWall = game.canExplodeNearWall(this, aim)
        if (this.hasWeapon(WeaponType.ROCKET_LAUNCHER) && canExplodeNearWall)
            return false

        return true
    }

    private fun Game.nextTileRight(unit: model.Unit)
            = level.tiles[(unit.position.x + 1).toInt()][(unit.position.y).toInt()]

    private fun Game.nextTileLeft(unit: model.Unit)
            = level.tiles[(unit.position.x - 1).toInt()][(unit.position.y).toInt()]

    private fun Game.canExplodeNearWall(from: model.Unit, aim: Vec2Double): Boolean {
        var dx = 0.0
        // val pairs = arrayListOf<Pair<Int, Int>>()

        while (dx <= aim.x && dx < 5) {
            val targetX = from.position.x + dx

            val dy = aim.y * dx / aim.x // same angle
            val targetY = from.position.y + dy

            val isWall = level.tiles[targetX.toInt()][targetY.toInt()] == Tile.WALL
            if (isWall && distanceSqr(from.position, Vec2Double(targetX, targetY)) < 3) {
                println("Near wall at $targetX, $targetY")
                return true
            }
            /*
            val pair = Pair(targetX.toInt(), targetY.toInt())
            if (!pairs.contains(pair))
                pairs.add(pair)
            */

            dx += 0.6
        }
        // println(pairs)
        return false
    }


    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        val nearestEnemy = getNearestEnemy(unit, game)
        val nearestWeapon = getNearestWeapon(unit, game)
        val nearestHealthPack = getNearestHealthPack(unit, game)

        // Unit prefers to stay in the health pack
        var targetPos: Vec2Double = unit.position

        // unless doesn't have a weapon yet
        if (!unit.hasWeapon() && nearestWeapon != null) {
            targetPos = nearestWeapon.position
            // ("Target Weapon: ${targetPos.x}, ${targetPos.y}")
        } else if (nearestHealthPack != null /*&& unit.tookDamage(game)*/) {
            targetPos = nearestHealthPack.position
            // ("Target HealthPack: ${targetPos.x}, ${targetPos.y}")
        }/*else if (nearestEnemy != null) {
            targetPos = nearestEnemy.position
            // ("Target Enemy: ${targetPos.x}, ${targetPos.y}")
        }*/

        // println("Target pos: ${targetPos.x}, ${targetPos.y}")
        // println("Unit pos: ${unit.position.x}, ${unit.position.y}")
        // debug.draw(CustomData.Log("Target pos: $targetPos"))

        val aim = unit.aimTo(nearestEnemy)
        println("aim: $aim")

        val shoot = unit.shouldShoot(nearestEnemy, aim, game)

        var swapWeapon = false
        if (nearestWeapon != null) {
            // nearestWeapon is a LootBox
            val nearestWeaponType = (nearestWeapon.item as Item.Weapon).weaponType
            swapWeapon = unit.shouldSwapWeapon(nearestWeaponType)
        }

        var jump = targetPos.y > unit.position.y
        if (targetPos.x > unit.position.x && game.nextTileRight(unit) == Tile.WALL) {
            jump = true
        }
        if (targetPos.x < unit.position.x && game.nextTileLeft(unit) == Tile.WALL) {
            jump = true
        }

        val action = UnitAction()
        action.velocity = targetPos.x - unit.position.x
        action.jump = jump
        action.jumpDown = !jump
        action.aim = aim
        action.shoot = shoot
        action.reload = false
        action.swapWeapon = swapWeapon
        action.plantMine = false

        return action
    }

    companion object {
        internal fun distanceSqr(a: Vec2Double, b: Vec2Double): Double {
            return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        }
    }
}
