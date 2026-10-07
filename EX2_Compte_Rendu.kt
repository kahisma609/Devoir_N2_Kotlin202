import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ===========================
// PARTIE 1 : PRODUITS ET STOCK
// ===========================

class Produit(
    val id: Int,
    val nom: String,
    var quantite: Int
) {
    fun afficherDetails() {
        println("Produit #$id : $nom | Quantité : $quantite")
    }
}

class Stock {
    private val produits = mutableMapOf<Int, Produit>()

    private val _evenementsStock = MutableStateFlow<List<Produit>>(emptyList())
    val evenementsStock: StateFlow<List<Produit>> = _evenementsStock

    fun ajouterProduit(produit: Produit) {
        produits[produit.id] = produit
        emitEvenementStock()
        println("Produit ajouté : ${produit.nom}")
    }

    suspend fun ajouterQuantite(idProduit: Int, quantite: Int) {
        withContext(Dispatchers.Default) {
            val produit = produits[idProduit]

            if (produit == null) {
                println("Produit #$idProduit introuvable.")
                return@withContext
            }

            produit.quantite += quantite
            println("+$quantite unités pour ${produit.nom} (nouveau stock : ${produit.quantite})")
            emitEvenementStock()
        }
    }

    suspend fun retirerQuantite(idProduit: Int, quantite: Int) {
        withContext(Dispatchers.Default) {
            val produit = produits[idProduit]

            if (produit == null) {
                println("Produit #$idProduit introuvable.")
                return@withContext
            }

            if (produit.quantite < quantite) {
                println(
                    "Erreur : stock insuffisant pour ${produit.nom}. " +
                            "Disponible : ${produit.quantite}, demandé : $quantite"
                )
                return@withContext
            }

            produit.quantite -= quantite
            println("-$quantite unités pour ${produit.nom} (nouveau stock : ${produit.quantite})")
            emitEvenementStock()
        }
    }

    private fun emitEvenementStock() {
        _evenementsStock.value = produits.values.toList()
    }

    fun afficherStock() {
        println("=== Stock actuel ===")
        if (produits.isEmpty()) {
            println("Aucun produit en stock.")
        } else {
            for (produit in produits.values) {
                produit.afficherDetails()
            }
        }
    }

    fun getProduit(idProduit: Int): Produit? {
        return produits[idProduit]
    }
}

// ===========================
// PARTIE 2 : COMMANDES CLIENTS
// ===========================

data class Commande(
    val idCommande: Int,
    val produits: List<Pair<Int, Int>>
) {
    fun afficherCommande() {
        println("=== Commande #$idCommande ===")
        for ((id, quantite) in produits) {
            println("Produit #$id : $quantite unités")
        }
    }
}

class GestionnaireCommandes(
    private val stock: Stock
) {
    private val commandes = mutableListOf<Commande>()

    suspend fun traiterCommande(commande: Commande) {
        println("\nTraitement de la commande #${commande.idCommande}...")
        commande.afficherCommande()

        var toutDisponible = true

        for ((idProduit, quantite) in commande.produits) {
            val produit = stock.getProduit(idProduit)

            if (produit == null) {
                println("Produit #$idProduit introuvable dans le stock.")
                toutDisponible = false
                continue
            }

            if (produit.quantite < quantite) {
                println(
                    "Stock insuffisant pour le produit #$idProduit " +
                            "(${produit.nom}). Disponible : ${produit.quantite}, demandé : $quantite"
                )
                toutDisponible = false
            }
        }

        if (!toutDisponible) {
            println("Commande #${commande.idCommande} ANNULÉE (produits indisponibles).")
            return
        }

        for ((idProduit, quantite) in commande.produits) {
            stock.retirerQuantite(idProduit, quantite)
        }

        println("Commande #${commande.idCommande} traitée avec succès.")
    }

    suspend fun gererCommandes(commandes: List<Commande>) {
        coroutineScope {
            for (commande in commandes) {
                launch {
                    traiterCommande(commande)
                }
            }
        }
    }
}

// ===========================
// PARTIE 3 : OPÉRATIONS ASYNCHRONES
// ===========================

class Entrepot(
    val stock: Stock,
    val gestionnaireCommandes: GestionnaireCommandes
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun ajouterProduitAuStock(produit: Produit) {
        scope.launch {
            println("Ajout asynchrone du produit : ${produit.nom}")
            stock.ajouterProduit(produit)
        }
    }

    fun retirerProduitDuStock(idProduit: Int, quantite: Int) {
        scope.launch {
            println("Retrait asynchrone de $quantite unités du produit #$idProduit")
            stock.retirerQuantite(idProduit, quantite)
        }
    }

    suspend fun gererInventaire() {
        withContext(Dispatchers.Default) {
            println("\n=== Inventaire en cours ===")
            delay(800L)
            stock.afficherStock()
            println("Inventaire terminé.\n")
        }
    }

    fun stop() {
        scope.cancel()
    }
}

// ===========================
// PARTIE 4 : FLUX D'ÉVÉNEMENTS (FLOW)
// ===========================

suspend fun surveillerStockEnTempsReel(stock: Stock) {
    stock.evenementsStock.collect { listeProduits ->
        println("\n[ÉVÉNEMENT STOCK] Mise à jour détectée.")
        println("Nombre de produits différents : ${listeProduits.size}")

        for (produit in listeProduits) {
            println(" - ${produit.nom} : ${produit.quantite} unités")
        }
    }
}

// ===========================
// MAIN : QUESTIONS 1 À 4
// ===========================

fun main() = runBlocking {
    val stock = Stock()
    val gestionnaire = GestionnaireCommandes(stock)
    val entrepot = Entrepot(stock, gestionnaire)

    val jobSurveillance = launch {
        surveillerStockEnTempsReel(stock)
    }

    println("=== DÉBUT DE L'EXERCICE ===\n")

    // Question 1 : Ajouter des produits avec quantités initiales
    val produit1 = Produit(1, "Ordinateur", 10)
    val produit2 = Produit(2, "Clavier", 30)
    val produit3 = Produit(3, "Souris", 50)

    entrepot.ajouterProduitAuStock(produit1)
    entrepot.ajouterProduitAuStock(produit2)
    entrepot.ajouterProduitAuStock(produit3)

    delay(500L)

    // Question 2 : Retirer ou ajouter de manière asynchrone
    stock.ajouterQuantite(1, 5)
    entrepot.retirerProduitDuStock(2, 10)

    delay(600L)

    // Question 3 : Traiter plusieurs commandes clients en parallèle
    val commande1 = Commande(1, listOf(1 to 2, 3 to 3))
    val commande2 = Commande(2, listOf(2 to 5, 1 to 1))
    val commande3 = Commande(3, listOf(1 to 100))

    gestionnaire.gererCommandes(listOf(commande1, commande2, commande3))

    delay(1000L)

    // Question 4 : Inventaire et surveillance Flow
    entrepot.gererInventaire()

    delay(500L)

    println("=== FIN DE L'EXERCICE ===\n")

    jobSurveillance.cancel()
    entrepot.stop()
}