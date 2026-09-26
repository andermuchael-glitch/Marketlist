package com.andermuchael.marketlist

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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

data class HistoryItem(
    val name: String,
    val category: String,
    val price: Double,
    val quantity: Double
) {
    val total: Double get() = price * quantity
}

data class PurchaseHistory(
    val id: Long,
    val date: String,
    val total: Double,
    val count: Int,
    val items: List<HistoryItem> = emptyList()
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
            val savedItems = mutableListOf<HistoryItem>()
            val itemArray = o.optJSONArray("items") ?: JSONArray()
            for (j in 0 until itemArray.length()) {
                val item = itemArray.getJSONObject(j)
                savedItems += HistoryItem(
                    item.optString("name"),
                    item.optString("category", "Outros"),
                    item.optDouble("price", 0.0),
                    item.optDouble("quantity", 1.0)
                )
            }
            PurchaseHistory(
                o.optLong("id"),
                o.optString("date"),
                o.optDouble("total"),
                o.optInt("count"),
                savedItems
            )
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
                put("id", h.id)
                put("date", h.date)
                put("total", h.total)
                put("count", h.count)
                put("items", JSONArray().apply {
                    h.items.forEach { item ->
                        put(JSONObject().apply {
                            put("name", item.name)
                            put("category", item.category)
                            put("price", item.price)
                            put("quantity", item.quantity)
                        })
                    }
                })
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

    fun archiveBought() {
        val boughtItems = items.filter { it.bought }
        if (boughtItems.isEmpty()) return
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        val historyItems = boughtItems.map { HistoryItem(it.name, it.category, it.price, it.quantity) }
        val entry = PurchaseHistory(
            System.currentTimeMillis(), date,
            boughtItems.sumOf { it.total }, boughtItems.size, historyItems
        )
        history = (listOf(entry) + history).take(30)
        saveHistory()
        items = items.filterNot { it.bought }
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
    var showAddSheet by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var budgetText by remember(vm.budget) { mutableStateOf(if (vm.budget == 0.0) "" else vm.budget.toString()) }
    var currentFilter by remember { mutableStateOf("Todos") }
    var selectedNav by remember { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2563EB),
            secondary = Color(0xFF10B981),
            background = Color(0xFFF6F8FC),
            surface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Marketlist", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text("Sua lista de compras", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.showHistory = true }) {
                            Icon(Icons.Default.History, "Histórico de compras", tint = Color.White)
                        }
                    }
                )
            },
            bottomBar = { NavigationBar(containerColor = Color.White) { NavigationBarItem(selected = selectedNav == 0, onClick = { selectedNav = 0 }, icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Lista") }); NavigationBarItem(selected = selectedNav == 1, onClick = { selectedNav = 1; vm.showHistory = true }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Histórico") }); NavigationBarItem(selected = selectedNav == 2, onClick = { selectedNav = 2 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Código") }); NavigationBarItem(selected = selectedNav == 3, onClick = { selectedNav = 3; showBudget = true }, icon = { Icon(Icons.Default.MoreVert, null) }, label = { Text("Mais") }) } },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Novo produto") },
                    containerColor = Color(0xFF176BEE),
                    contentColor = Color.White
                )
            }
        ) { padding ->
            val visible = vm.items.filter {
                it.name.contains(vm.search, ignoreCase = true) &&
                    when (currentFilter) {
                        "Pendentes" -> !it.bought
                        "Comprados" -> it.bought
                        else -> true
                    }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                // Resumo compacto: não ocupa a área principal da lista.
                Card(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("TOTAL", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                            Text(
                                money(vm.total),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        VerticalDivider(
                            modifier = Modifier.height(34.dp),
                            color = Color.White.copy(alpha = .25f)
                        )
                        Column(
                            Modifier
                                .padding(horizontal = 14.dp)
                                .clickable { showBudget = true },
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("ORÇAMENTO", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (vm.budget > 0) money(vm.budget) else "Definir",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = vm.search,
                    onValueChange = { vm.search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar na lista") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (vm.search.isNotEmpty()) {
                            IconButton(onClick = { vm.search = "" }) {
                                Icon(Icons.Default.Close, "Limpar busca")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(Modifier.height(7.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Todos", "Pendentes", "Comprados").forEach { filter ->
                        FilterChip(
                            selected = currentFilter == filter,
                            onClick = { currentFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (vm.bought > 0) {
                        TextButton(onClick = vm::clearBought) {
                            Text("Limpar")
                        }
                    }
                }

                if (visible.isEmpty()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                null,
                                Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = .45f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (vm.items.isEmpty()) "Sua lista está vazia" else "Nenhum item encontrado",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                if (vm.items.isEmpty()) "Toque em + para adicionar seu primeiro produto."
                                else "Tente outra busca ou filtro."
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(top = 5.dp, bottom = 96.dp)
                    ) {
                        items(visible, key = { it.id }) { item ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .alpha(if (item.bought) .62f else 1f),
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
                                            modifier = Modifier.widthIn(min = 30.dp),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        IconButton(onClick = { vm.changeQuantity(item.id, 1.0) }) {
                                            Icon(Icons.Default.Add, "Aumentar quantidade")
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        OutlinedTextField(
                                            value = if (item.price == 0.0) "" else item.price.toString(),
                                            onValueChange = { vm.updatePrice(item.id, it) },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Preço no mercado") },
                                            prefix = { Text("R$ ") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Novo produto", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Monte sua lista agora. O preço pode ser informado depois, no mercado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = vm.quickName,
                        onValueChange = { vm.quickName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Produto") },
                        placeholder = { Text("Ex.: Arroz 5 kg") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { categoryMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(vm.quickCategory)
                            }
                            DropdownMenu(
                                expanded = categoryMenuOpen,
                                onDismissRequest = { categoryMenuOpen = false }
                            ) {
                                vm.categories().forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            vm.quickCategory = category
                                            categoryMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                vm.addQuickItem()
                                showAddSheet = false
                            },
                            enabled = vm.quickName.trim().isNotEmpty(),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Adicionar")
                        }
                    }
                }
            }
        }

        if (showBudget) {
            AlertDialog(
                onDismissRequest = { showBudget = false },
                title = { Text("Orçamento da compra") },
                text = {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it },
                        label = { Text("Valor máximo") },
                        prefix = { Text("R$ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        vm.setBudget(budgetText)
                        showBudget = false
                    }) { Text("Salvar") }
                },
                dismissButton = {
                    TextButton(onClick = { showBudget = false }) { Text("Cancelar") }
                }
            )
        }

        if (vm.showHistory) {
            AlertDialog(
                onDismissRequest = { vm.showHistory = false },
                title = {
                    Column {
                        Text("Histórico de compras")
                        Text(
                            "Compras arquivadas com itens, quantidades e valores.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                text = {
                    if (vm.history.isEmpty()) {
                        Text("Nenhuma compra arquivada ainda.")
                    } else {
                        Column(
                            Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            vm.history.take(10).forEach { h ->
                                Card {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(h.date, style = MaterialTheme.typography.titleSmall)
                                            Text(money(h.total), style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text("${h.count} item(ns)", style = MaterialTheme.typography.labelMedium)
                                        h.items.forEach { item ->
                                            Text(
                                                "• ${item.name} — ${formatQuantity(item.quantity)} x ${money(item.price)} = ${money(item.total)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.showHistory = false }) { Text("Fechar") }
                },
                dismissButton = {
                    if (vm.history.isNotEmpty()) TextButton(onClick = vm::clearHistory) { Text("Apagar histórico") }
                }
            )
        }
    }
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
