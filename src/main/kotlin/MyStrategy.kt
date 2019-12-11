import model.*
import model.Unit
import kotlin.math.abs
import kotlin.math.max

class MyStrategy {

    private fun getNearestEnemy(unit: model.Unit, game: Game): model.Unit? {
        var nearestEnemy: Unit? = null
        for (other in game.getOtherUnits(unit)) {
            if (nearestEnemy == null || unit.distanceTo(other.position) < unit.distanceTo(nearestEnemy.position)) {
                nearestEnemy = other
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

    private fun Unit.distanceTo(certainPosition: Vec2Double)
            = distanceSqr(this.position, certainPosition)

    private fun Unit.distanceFromCenter(certainPosition: Vec2Double)
            = distanceSqr(this.centerPosition(), certainPosition)

    private fun Unit.centerPosition() = Vec2Double(position.x + size.x/2, position.y + size.y/2)

    private fun Unit.shouldShoot(nearestEnemy: model.Unit?, aim: Vec2Double, game: Game): Boolean {
        if (nearestEnemy == null)
            return false

        // if enemy is on top, simply shoot
        if (nearestEnemy.isOnTopOf(this))
            return true

        // don't shoot if there is a wall in the middle
        val affectedWall = game.getNearestAffectedWall(this, aim)
        // this.hasWeapon(WeaponType.ROCKET_LAUNCHER)
        if (affectedWall != null && this.distanceTo(nearestEnemy.position) > this.distanceTo(affectedWall))
            return false

        return true
    }

    private fun Unit.isOnTopOf(unit: Unit): Boolean {
        return this.position.y > unit.position.y && abs(this.position.x - unit.position.x) < 0.7
    }

    private fun Unit.nearBullets(game: Game, weaponType: WeaponType)
            = game.bullets.filter { bullet ->
                bullet.weaponType == weaponType
                && bullet.playerId != playerId
                && distanceFromCenter(bullet.position) < NEAR_BULLETS_DISTANCE
            }

    private fun Unit.dodgeBullet(bullet: Bullet): Vec2Double {
        println(
            "bullet ${format(bullet.position)}, " +
            "collision ${willCollision(bullet)}, " +
            "comingHorizontally ${bullet.isComingHorizontallyTo(position)}"
        )

        // TODO: detect where is better to move & be safe
        if (willCollision(bullet) && bullet.isComingHorizontallyTo(position)) {
            return Vec2Double(position.x, position.y + 1)
        }

        return position
    }

    private fun Unit.willCollision(bullet: Bullet): Boolean {
        // bullet position considers the center of it
        val x1 = bullet.position.x - bullet.size/2
        val x2 = bullet.position.x + bullet.size/2
        val y1 = bullet.position.y - bullet.size/2
        val y2 = bullet.position.y + bullet.size/2

        // unit position considers the bottom middle point
        val x3 = position.x - size.x/2
        val x4 = position.x + size.x/2
        val y3 = position.y
        val y4 = position.y + size.y

        val left = max(x1, x3)
        val top = max(y2, y4)
        val right = max(x2, x4)
        val bottom = max(y1, y3)

        val width = right - left
        val height = top - bottom

        if (width<0 || height<0)
            return false

        return true
    }

    private fun Bullet.isComingHorizontallyTo(certainPosition: Vec2Double): Boolean {
        val bulletAtTheRight = certainPosition.x < position.x
        val movingToLeft = velocity.x < 0

        if (bulletAtTheRight && movingToLeft)
            return true

        val bulletAtTheLeft = position.x < certainPosition.x
        val movingToRight = velocity.x > 0

        if (bulletAtTheLeft && movingToRight)
            return true

        return false
    }

    private fun model.Unit.canNotMoveRight(game: Game): Boolean {
        return game.nextTileRight(this) == Tile.WALL || game.thereIsUnitAtTheRight(this)
    }

    private fun model.Unit.canNotMoveLeft(game: Game): Boolean {
        return game.nextTileLeft(this) == Tile.WALL || game.thereIsUnitAtTheLeft(this)
    }

    private fun model.Unit.shouldReload(): Boolean {
        // TODO: Consider reloadTime & opponent distance
        weapon?.let {
            val minBulletsStock = it.params.magazineSize / 2
            // print("magazine = ${it.magazine}, ")
            if (weapon != null && it.magazine < minBulletsStock) {
                return true
            }
        }

        return false
    }

    private fun Game.nextTileRight(unit: model.Unit)
            = level.tiles[(unit.position.x + 1).toInt()][(unit.position.y).toInt()]

    private fun Game.nextTileLeft(unit: model.Unit)
            = level.tiles[(unit.position.x - 1).toInt()][(unit.position.y).toInt()]

    private fun Game.getNearestAffectedWall(from: model.Unit, aim: Vec2Double): Vec2Double? {
        var dx = 0.0

        while (aim.x > 0 && dx <= aim.x || aim.x < 0 && dx >= aim.x) {
            val targetX = from.position.x + dx

            val dy = aim.y * dx / aim.x // same angle
            val targetY = from.position.y + dy

            val isWall = level.tiles[targetX.toInt()][targetY.toInt()] == Tile.WALL

            if (isWall) {
                // println("Nearest affected wall at $targetX, $targetY")
                return Vec2Double(targetX, targetY)
            }

            if (aim.x > 0)
                dx += 0.6
            else
                dx -= 0.6
        }
        // println(pairs)
        return null
    }

    private fun Game.getOtherUnits(excludedUnit: Unit): List<Unit> {
        return units.filter { it.playerId != excludedUnit.playerId }
    }

    private fun Game.thereIsUnitAtTheRight(mainUnit: Unit): Boolean {
        for (other in getOtherUnits(mainUnit)) {
            if (other.position.x.toInt() == mainUnit.position.x.toInt() +1) {
                return true
            }
        }
        return false
    }

    private fun Game.thereIsUnitAtTheLeft(mainUnit: Unit): Boolean {
        for (other in getOtherUnits(mainUnit)) {
            if (other.position.x.toInt() == mainUnit.position.x.toInt() -1) {
                return true
            }
        }
        return false
    }

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        print("tick ${game.currentTick}: ")

        val nearestEnemy = getNearestEnemy(unit, game)
        val nearestWeapon = getNearestWeapon(unit, game)
        val nearestHealthPack = getNearestHealthPack(unit, game)

        // TODO: if there is a better weapon near, just take it
        // TODO: if there is a rocket launcher near & the enemy is just in front, grab it

        // TODO: dodge bullets considering explosion area

        // By priority, unit prefers to:
        // 1- Look for a weapon
        // 2- Dodge Rocket Launcher bullets
        // 3- Look for a health pack / stay there
        // 4- Dodge other bullets
        // 4- Take a better weapon / swap if ammo is almost gone

        var targetPos: Vec2Double = unit.position
        var targetLabel = "None"

        val rocketBullets = unit.nearBullets(game, WeaponType.ROCKET_LAUNCHER)

        // 1-
        if (!unit.hasWeapon() && nearestWeapon != null) {
            targetPos = nearestWeapon.position
            targetLabel = "Weapon"
        } else if (rocketBullets.isNotEmpty()) {
            targetPos = unit.dodgeBullet(rocketBullets.first())
            targetLabel = "DodgeRocket"
        } else if (nearestHealthPack != null /*&& unit.tookDamage(game)*/) {
            val healthPackPos = nearestHealthPack.position
            if (healthPackPos.isOverPlatform(game))
                healthPackPos.y += 0.3
            targetPos =  healthPackPos
            targetLabel = "Health"
        }/*else if (nearestEnemy != null) {
            targetPos = nearestEnemy.position
        }*/

        val aim = unit.aimTo(nearestEnemy)

        val shoot = unit.hasWeapon() && unit.shouldShoot(nearestEnemy, aim, game)

        var swapWeapon = false
        if (nearestWeapon != null) {
            // nearestWeapon is a LootBox
            val nearestWeaponType = (nearestWeapon.item as Item.Weapon).weaponType
            swapWeapon = unit.shouldSwapWeapon(nearestWeaponType)
        }

        var jump = targetPos.y > unit.position.y
        if (targetPos.x > unit.position.x && unit.canNotMoveRight(game)) {
            jump = true
        }
        if (targetPos.x < unit.position.x && !unit.canNotMoveLeft(game)) {
            jump = true
        }

        val reload = if (shoot) {
            false
        } else {
            unit.shouldReload()
        }

        val action = UnitAction()
        action.velocity = adjustVelocity(targetPos.x - unit.position.x)
        action.jump = jump
        action.jumpDown = !jump
        action.aim = aim
        action.shoot = shoot
        action.reload = reload
        action.swapWeapon = swapWeapon
        action.plantMine = false

        debug.draw(CustomData.Log(
        "unit ${format(unit.position)}, " +
            // "aim = ${format(aim)}, " +
            "enemy ${format(nearestEnemy?.position ?: Vec2Double())}, " +
            "velX ${format(action.velocity)}, " +
            // "shoot = $shoot, " +
            "target ${targetInfo(targetLabel, targetPos)}"
        ))

        return action
    }

    private fun Vec2Double.isOverPlatform(game: Game): Boolean {
        return game.level.tiles[x.toInt()][y.toInt()-1] == Tile.PLATFORM
    }

    private fun adjustVelocity(velocity: Double): Double {
        /*if (velocity > 0.0 && velocity < 1.0) {
            return velocity + 1
        }
        if (velocity > -1.0 && velocity < 0.0) {
            return  velocity - 1
        }*/
        val vel = abs(velocity)

        if (vel >= 1.7 && vel < 2.7) {
            return velocity * 2
        }

        if (vel > 0.5 && vel < 1.7) {
            return velocity * 3
        }

        if (vel <= 0.5) {
            return velocity * 4
        }

        return velocity
    }

    private fun targetInfo(label: String, position: Vec2Double): String {
        if (label == "None")
            return label

        return "$label ${format(position)})"
    }

    companion object {
        internal fun distanceSqr(a: Vec2Double, b: Vec2Double): Double {
            return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        }

        const val NEAR_BULLETS_DISTANCE = 12
    }

    private fun format(v: Vec2Double): String {
        return "(${v.x.fewDecimals()}, ${v.y.fewDecimals()})"
    }

    private fun format(d: Double): String {
        return d.fewDecimals()
    }

    private fun Double.fewDecimals(): String {
        return String.format("%.2f", this)
    }
}
