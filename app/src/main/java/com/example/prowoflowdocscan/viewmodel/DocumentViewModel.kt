package com.example.prowoflowdocscan.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.prowoflowdocscan.data.Document
import com.example.prowoflowdocscan.data.DocumentRepository
import com.example.prowoflowdocscan.util.OcrHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    val repository = DocumentRepository(application)

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()

    private val _filteredDocuments = MutableStateFlow<List<Document>>(emptyList())
    val filteredDocuments: StateFlow<List<Document>> = _filteredDocuments.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortByName = MutableStateFlow(false)
    val sortByName: StateFlow<Boolean> = _sortByName.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()

    // For DocumentDetailScreen
    private val _isSearchable = MutableStateFlow(false)
    val isSearchable: StateFlow<Boolean> = _isSearchable.asStateFlow()

    private val _allOcrHighlights = MutableStateFlow<List<OcrHighlight>>(emptyList())
    val allOcrHighlights: StateFlow<List<OcrHighlight>> = _allOcrHighlights.asStateFlow()

    private val _searchHighlightRects = MutableStateFlow<List<OcrHighlight>>(emptyList())
    val searchHighlightRects: StateFlow<List<OcrHighlight>> = _searchHighlightRects.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(0)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    private val _detailSearchQuery = MutableStateFlow("")
    val detailSearchQuery: StateFlow<String> = _detailSearchQuery.asStateFlow()

    private val _ocrDataRefreshTrigger = MutableStateFlow(0)
    val ocrDataRefreshTrigger: StateFlow<Int> = _ocrDataRefreshTrigger.asStateFlow()

    init {
        loadDocuments()
    }

    fun triggerRefresh() {
        _refreshTrigger.value += 1
        loadDocuments()
    }

    fun loadDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = repository.getDocuments()
            _documents.value = docs
            updateFilteredDocuments(docs, _searchQuery.value, _sortByName.value)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        updateFilteredDocuments(_documents.value, query, _sortByName.value)
    }

    fun toggleSortByName() {
        _sortByName.value = !_sortByName.value
        updateFilteredDocuments(_documents.value, _searchQuery.value, _sortByName.value)
    }

    private fun updateFilteredDocuments(docs: List<Document>, query: String, sortByName: Boolean) {
        val filtered = if (query.isBlank()) docs else docs.filter { it.name.contains(query, ignoreCase = true) }
        _filteredDocuments.value = if (sortByName) {
            filtered.sortedBy { it.name }
        } else {
            filtered.sortedByDescending { it.dateModified }
        }
    }

    fun triggerOcrDataRefresh(documentId: String) {
        _ocrDataRefreshTrigger.value += 1
        checkIfSearchable(documentId)
        loadOcrHighlights(documentId)
    }

    fun checkIfSearchable(documentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchable.value = repository.isSearchable(documentId)
        }
    }

    fun loadOcrHighlights(documentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val highlights = repository.getOcrHighlights(documentId)
            _allOcrHighlights.value = highlights
            updateSearchHighlights(highlights, _detailSearchQuery.value)
        }
    }

    fun setDetailSearchQuery(query: String) {
        _detailSearchQuery.value = query
        _currentSearchIndex.value = 0
        updateSearchHighlights(_allOcrHighlights.value, query)
    }

    private fun updateSearchHighlights(highlights: List<OcrHighlight>, query: String) {
        _searchHighlightRects.value = if (query.isBlank()) emptyList()
        else highlights.filter { it.text.contains(query, ignoreCase = true) }
    }

    fun setCurrentSearchIndex(index: Int) {
        _currentSearchIndex.value = index
    }
}
