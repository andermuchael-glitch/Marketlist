package com.andermuchael.marketlist

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS = "marketlist"
private const val ITEMS_KEY = "items"
private const val BUDGET_KEY = "budget"
private const val HISTORY_KEY = "history"

data class ShoppingItem(
    val id: Long,
    val name: String,
    val category: String = "Outros",
    val price: Double = 0.0,
    val quantity: Double = 1.0,
    val bought: Boolean = false
) {
    val total: Double get() = price * quantity
}

data class PurchaseHistory(
    val id: Long,
    val date: String,
    val total: Double,
    val count: Int
)

class MarketViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS, 0)

    var items by mutableStateOf(loadItems())
        private set
    var budget by mutableStateOf(prefs.getString(BUDGET_KEY, "0")?.toDoubleOrNull() ?: 0.0)
        private set
    var history by mutableStateOf(loadHistory())
        private set
    var quickName by mutableStateOf("")
    var quickCategory by mutableStateOf("Outros")
    var search by mutableStateOf("")
    var showHistory by mutableStateOf(false)

    val total get() = items.sumOf { it.total }
    val pending get() = items.count { !it.bought }
    val bought get() = items.count { it.bought }
    val remainingBudget get() = budget - total

    private val categoryOptions = listOf("Outros", "Hortifruti", "Carnes", "Laticínios", "Padaria", "Limpeza", "Bebidas", "Higiene", "Mercearia")
    fun categories() = categoryOptions

    private fun loadItems(): List<ShoppingItem> = runCatching {
        val array = JSONArray(prefs.getString(ITEMS_KEY, "[]") ?: "[]")
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            ShoppingItem(
                id = o.optLong("id"),
                name = o.optString("name"),
                category = o.optString("category", "Outros"),
                price = o.optDouble("price", 0.0),
                quantity = o.optDouble("quantity", 1.0).coerceAtLeast(0.01),
                bought = o.optBoolean("bought", false)
            )
        }
    }.getOrDefault(emptyList())

    private fun loadHistory(): List<PurchaseHistory> = runCatching {
        val array = JSONArray(prefs.getString(HISTORY_KEY, "[]") ?: "[]")
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            PurchaseHistory(o.optLong("id"), o.optString("date"), o.optDouble("total"), o.optInt("count"))
        }
    }.getOrDefault(emptyList())

    private fun saveItems() {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("category", item.category)
                put("price", item.price); put("quantity", item.quantity); put("bought", item.bought)
            })
        }
        prefs.edit().putString(ITEMS_KEY, array.toString()).apply()
    }

    private fun saveHistory() {
        val array = JSONArray()
        history.forEach { h ->
            array.put(JSONObject().apply {
                put("id", h.id); put("date", h.date); put("total", h.total); put("count", h.count)
            })
        }
        prefs.edit().putString(HISTORY_KEY, array.toString()).apply()
    }

    fun addQuickItem() {
        val name = quickName.trim()
        if (name.isEmpty()) return
        items = listOf(ShoppingItem(System.currentTimeMillis(), name, quickCategory)) + items
        quickName = ""
        saveItems()
    }

    fun updatePrice(id: Long, text: String) {
        val value = text.replace(",", ".").toDoubleOrNull() ?: 0.0
        items = items.map { if (it.id == id) it.copy(price = value.coerceAtLeast(0.0)) else it }
        saveItems()
    }

    fun changeQuantity(id: Long, delta: Double) {
        items = items.map { if (it.id == id) it.copy(quantity = (it.quantity + delta).coerceAtLeast(0.01)) else it }
        saveItems()
    }

    fun toggle(id: Long) {
        items = items.map { if (it.id == id) it.copy(bought = !it.bought) else it }
        saveItems()
    }

    fun delete(id: Long) {
        items = items.filterNot { it.id == id }
        saveItems()
    }

    fun clearBought() {
        items = items.filterNot { it.bought }
        saveItems()
    }

    fun setBudget(text: String) {
        budget = text.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        prefs.edit().putString(BUDGET_KEY, budget.toString()).apply()
    }

    fun finishShopping() {
        if (items.isEmpty()) return
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        history = (listOf(PurchaseHistory(System.currentTimeMillis(), date, total, items.size)) + history).take(30)
        saveHistory()
        items = emptyList()
        saveItems()
    }

    fun clearHistory() {
        history = emptyList()
        saveHistory()
    }
}

private fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarketlistApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketlistApp(vm: MarketViewModel = viewModel()) {
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var budgetText by remember(vm.budget) { mutableStateOf(if (vm.budget == 0.0) "" else vm.budget.toString()) }
    var categoryFilter by remember { mutableStateOf("Todas") }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF2563EB),
        secondary = Color(0xFF10B981),
        background = Color(0xFFF6F8FC),
        surface = Color.White
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Marketlist", style = MaterialTheme.typography.titleLarge)
                            Text("Sua lista de compras", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.showHistory = true }) {
                            Icon(Icons.Default.History, "Histórico")
                        }
                    }
                )
            }
        ) { padding ->
            val visible = vm.items.filter {
                it.name.contains(vm.search, ignoreCase = true) &&
                    (categoryFilter == "Todas" || it.category == categoryFilter)
            }

            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Card(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("TOTAL", color = Color.White.copy(alpha = .75f))
                        Text(money(vm.total), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("${vm.pending} pendentes  •  §{vm.bought} comprados", color = Color.White)
                        if (vm.budget > 0) {
                            Text(
                                if (vm.remainingBudget >= 0) "Restante: §{money(vm.remainingBudget)}"
                                else "Acima do orçamento: §{money(-vm.remainingBudget)}",
                                color = if (vm.remainingBudget >= 0) Color.White else Color(0xFFFFD6D6)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = budgetText, onValueChange = { budgetText = it },
                        modifier = Modifier.weight(1f), label = { Text("Orçamento") },
                        placeholder = { Text("R$ 300") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Button(onClick = { vm.setBudget(budgetText) }, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("Salvar")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = vm.quickName, onValueChange = { vm.quickName = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("O que você precisa comprar?") },
                        singleLine = true
                    )
                    Box {
                        OutlinedButton(onClick = { categoryMenuOpen = true }) { Text(vm.quickCategory.take(10)) }
                        DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                            vm.categories().forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = { vm.quickCategory = category; categoryMenuOpen = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = vm::addQuickItem, enabled = vm.quickName.trim().isNotEmpty()) {
                        Icon(Icons.Default.Add, "Adicionar")
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = vm.search, onValueChange = { vm.search = it },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar item...") },
                    singleLine = true
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = categoryFilter == "Todas", onClick = { categoryFilter = "Todas" }, label = { Text("Todos") })
                    vm.categories().filter { category -> vm.items.any { it.category == category } }.forEach { category ->
                        FilterChip(selected = categoryFilter == category, onClick = { categoryFilter = category }, label = { Text(category) })
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = vm::clearBought, enabled = vm.bought > 0) { Text("Limpar comprados") }
                }

                if (visible.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ShoppingCart, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .5f))
                            Text("Sua lista está vazia", style = MaterialTheme.typography.titleLarge)
                            Text("Adicione os produtos acima.")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp)
                    ) {
                        items(visible, key = { it.id }) { item ->
                            Card(
                                Modifier.fillMaxWidth().alpha(if (item.bought) .65f else 1f),
                                RoundedCornerShape(18.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { vm.toggle(item.id) }) {
                                            Icon(
                                                if (item.bought) Icons.Default.CheckCircle else Icons.Default.ShoppingCart,
                                                null,
                                                tint = if (item.bought) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                item.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                            Text(item.category, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text(money(item.total), style = MaterialTheme.typography.titleMedium)
                                        IconButton(onClick = { vm.delete(item.id) }) {
                                            Icon(Icons.Default.Delete, "Excluir")
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { vm.changeQuantity(item.id, -1.0) }) {
                                            Icon(Icons.Default.Remove, "Diminuir quantidade")
                                        }
                                        Text(
                                            if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString(),
                                            modifier = Modifier.widthIn(min = 32.dp),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        IconButton(onClick = { vm.changeQuantity(item.id, 1.0) }) {
                                            Icon(Icons.Default.Add, "Aumentar quantidade")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedTextField(
                                            value = if (item.price == 0.0) "" else item.price.toString(),
                                            onValueChange = { vm.updatePrice(item.id, it) },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Preço no mercado") },
                                            prefix = { Text("R$ ") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (vm.items.isNotEmpty()) {
                    Button(onClick = vm::finishShopping, modifier = Modifier.fillMaxWidth()) {
                        Text("✓ Finalizar e salvar compra")
                    }
                }
            }
        }

        if (vm.showHistory) {
            AlertDialog(
                onDismissRequest = { vm.showHistory = false },
                title = { Text("Histórico de compras") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.history.isEmpty()) Text("Nenhuma compra salva.")
                        else vm.history.take(8).forEach { h ->
                            Card {
                                Column(Modifier.padding(10.dp)) {
                                    Text(h.date, style = MaterialTheme.typography.titleSmall)
                                    Text("§{money(h.total)} • §{h.count} item(ns)")
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { vm.showHistory = false }) { Text("Fechar") } },
                dismissButton = {
                    if (vm.history.isNotEmpty()) TextButton(onClick = vm::clearHistory) { Text("Apagar histórico") }
                }
            )
        }
    }
}
