# Lagmonitor

6th May 2026

---

Minecraft Versions: 26.1

Paper Version: 1.21.x

Minimum Java Version: Java 21

Recommended Java Version: Java 25

Lagmonitor checks for placed **Boats**, **Armor Stands** and **Minecarts** within an area around a player and as they are placed, to help prevent lag through cramming and general abuse.

## Features
* Automated scan of a defined area around a player for a maximum number of entities.
* Separate configuration options for different supported entities.
* Checks when entity is placed.
* Ability to scale the scan to match a % of the simulation distance.

## Supported Entities
* Boats (including chest boats etc..)
* Armor Stands
* Minecarts (include chest minecarts etc..)


## Installation & Configuration
Noting every sever is different, it is important to configure ViewDistanceTweaks before using it.

To install the plugin:
1. Download the jar file in your plugin directory.
2. Restart the server.
3. Edit the plugin's configuration file to suit your server's needs.
4. Set lagmonitor.enabled to true in the plugin's configuration file.
5. Restart the server.

```
# Lagmonitor Configuration
# Set to false to disable all plugin functionality (proactive blocks and reactive scans)
lagmonitor-enabled: true

# The maximum number of boats allowed in a single scan size
lag-max-boats-per-scan: 20
# The maximum number of armor stands
lag-max-armor-per-scan: 50
# The maximum number of minecarts
lag-max-minecarts-per-scan: 20
# How often (in ticks) the server scans for boat clusters
# 20 ticks = 1 second. 100 ticks = 5 seconds.
lag-scan-interval-ticks: 100
# How wide the scan around a player is performed. This
# is in blocks.
lag-scan-x-size: 16
lag-scan-y-size: 8
lag-scan-z-size: 16
# Should the result of the scan be reported to all admins
# who have lagmonitor.notify perms?
lag-report-to-admins: true
# Should proactive blocks (blocking placement) be reported to admins?
lag-report-blocks-to-admins: true

# Per-block stacking limits (to prevent cramming lag machines)
lag-max-per-block-limit-enabled: true
lag-max-boats-per-block: 5
lag-max-armor-per-block: 5
lag-max-minecarts-per-block: 5

# Action to take when limits are exceeded during a scan.
# REMOVE: Entities are automatically removed (default).
# WARN: Admins are notified but no entities are removed.
lag-scan-action: REMOVE

# Dynamic Scan Radius (requires ViewDistanceTweaks or Paper 1.18+)
# If enabled, the scan radius will match the simulation distance.
lag-use-dynamic-scan-radius: true
# Multiplier for the simulation distance (1.0 = full sim distance)
lag-dynamic-radius-multiplier: 1.0
```

## Commands


**/lagmonitor reload** - Reloads the configuration file. (**Permission:** lagmonitor.reload)

**/lagmonitor scan** - Performs a manual scan for entities. (**Permission:** lagmonitor.scan)

**/lagmonitor clear** - Clears any reported scan results. (**Permission:** lagmonitor.clear)

## Permissions

```
permissions:
  lagmonitor.notify:
    description: Allows receiving notifications from Lagmonitor
    default: op
  lagmonitor.reload:
    description: Allows reloading the Lagmonitor configuration
    default: op
  lagmonitor.clear:
    description: Allows the manual removal of excessive entities.
    default: op
  lagmonitor.scan:
    description: Allows areas to be manually scanned for excessive entities.
    default: op
  lagmonitor.admin:
    description: Full administrative access to Lagmonitor
    default: op
    children:
      lagmonitor.notify: true
      lagmonitor.reload: true
      lagmonitor.clear: true
      lagmonitor.scan: true
```
